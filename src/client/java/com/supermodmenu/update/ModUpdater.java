package com.supermodmenu.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.supermodmenu.SuperModMenuClient;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-click mod updates.
 *
 * The hard part is that the old JAR is loaded (and, on Windows, locked) for the
 * whole game session, so it can't be replaced in place. Strategy:
 *
 *   1. Download the new version into a staging folder OUTSIDE {@code mods/}
 *      (two versions in {@code mods/} would make Fabric refuse to start).
 *   2. Register a JVM shutdown hook that launches a tiny detached OS helper.
 *   3. After Minecraft exits and releases the file locks, the helper deletes the
 *      old JAR and moves the staged JAR into {@code mods/}.
 *
 * Everything is applied atomically per mod on the next launch. If the game
 * crashes before exiting cleanly, staged files are cleared on the next startup.
 */
public final class ModUpdater {

    private static final String API = "https://api.modrinth.com/v2";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private static final Path STAGE = FabricLoader.getInstance().getConfigDir()
            .resolve("supermodmenu").resolve("pending-updates");

    public enum State { IDLE, DOWNLOADING, READY, ERROR }

    private static final Map<String, State>  states   = new ConcurrentHashMap<>();
    private static final Map<String, String> messages = new ConcurrentHashMap<>();

    private record Swap(String oldJar, String stagedJar, String finalName) {}
    private static final List<Swap> swaps = Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean hookInstalled = false;

    private ModUpdater() {}

    public static State  state(String modId)   { return states.getOrDefault(modId, State.IDLE); }
    public static String message(String modId) { return messages.getOrDefault(modId, ""); }

    /** Clears orphaned staged files left by a previous crashed session. */
    public static void cleanupStaging() {
        try {
            if (!Files.isDirectory(STAGE)) return;
            try (var s = Files.list(STAGE)) {
                s.forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
            }
        } catch (Exception ignored) {}
    }

    /**
     * Downloads the latest compatible version of {@code mod} into staging and
     * schedules the swap for the next game exit.
     */
    public static void update(ModContainer mod, String projectId, String mcVersion) {
        String id = mod.getMetadata().getId();
        if (state(id) == State.DOWNLOADING || state(id) == State.READY) return;

        Path oldJar;
        try {
            oldJar = mod.getOrigin().getPaths().get(0);
        } catch (Exception e) {
            states.put(id, State.ERROR); messages.put(id, "Can't locate installed JAR");
            return;
        }
        if (oldJar == null || !oldJar.toString().toLowerCase().endsWith(".jar")) {
            states.put(id, State.ERROR); messages.put(id, "Installed file isn't a JAR");
            return;
        }
        final Path oldJarF = oldJar.toAbsolutePath();

        states.put(id, State.DOWNLOADING);
        messages.put(id, "Downloading update…");

        CompletableFuture.runAsync(() -> {
            try {
                String url = API + "/project/" + projectId + "/version?loaders="
                        + enc("[\"fabric\"]") + "&game_versions=" + enc("[\"" + mcVersion + "\"]");
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                        .header("User-Agent", SuperModMenuClient.USER_AGENT).GET()
                        .timeout(Duration.ofSeconds(15)).build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) { err(id, "Modrinth HTTP " + resp.statusCode()); return; }

                JsonArray versions = JsonParser.parseString(resp.body()).getAsJsonArray();
                if (versions.isEmpty()) { err(id, "No compatible version"); return; }

                JsonObject file = primaryFile(versions.get(0).getAsJsonObject().getAsJsonArray("files"));
                if (file == null) { err(id, "No downloadable file"); return; }
                String dlUrl = file.get("url").getAsString();
                String fn = sanitize(file.get("filename").getAsString());
                if (!fn.endsWith(".jar")) { err(id, "Update isn't a JAR"); return; }

                Files.createDirectories(STAGE);
                Path staged = STAGE.resolve(fn);
                HttpRequest dlReq = HttpRequest.newBuilder().uri(URI.create(dlUrl))
                        .header("User-Agent", SuperModMenuClient.USER_AGENT).GET()
                        .timeout(Duration.ofSeconds(60)).build();
                HttpResponse<InputStream> dlResp = HTTP.send(dlReq, HttpResponse.BodyHandlers.ofInputStream());
                if (dlResp.statusCode() != 200) { err(id, "Download failed"); return; }
                try (InputStream in = dlResp.body()) {
                    Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
                }

                Swap swap = new Swap(oldJarF.toString(), staged.toAbsolutePath().toString(), fn);
                if (!isShellSafe(swap)) { err(id, "Unsafe path; update manually"); return; }
                swaps.add(swap);
                installHook();

                states.put(id, State.READY);
                messages.put(id, "Update ready — restart to apply");
            } catch (Exception e) {
                SuperModMenuClient.LOGGER.error("Update failed for {}", id, e);
                err(id, e.getMessage());
            }
        });
    }

    private static JsonObject primaryFile(JsonArray files) {
        if (files == null || files.isEmpty()) return null;
        for (int i = 0; i < files.size(); i++) {
            JsonObject f = files.get(i).getAsJsonObject();
            if (f.has("primary") && f.get("primary").getAsBoolean()) return f;
        }
        return files.get(0).getAsJsonObject();
    }

    private static void err(String id, String msg) {
        states.put(id, State.ERROR);
        messages.put(id, "Error: " + (msg == null ? "unknown" : msg));
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static boolean isShellSafe(Swap s) {
        for (String p : new String[]{s.oldJar, s.stagedJar, s.finalName}) {
            if (p.contains("\"") || p.contains("'") || p.contains("\n") || p.contains("&")
                    || p.contains("|") || p.contains(";") || p.contains("`")) return false;
        }
        return true;
    }

    private static synchronized void installHook() {
        if (hookInstalled) return;
        hookInstalled = true;
        Runtime.getRuntime().addShutdownHook(new Thread(ModUpdater::applyOnExit, "smm-updater"));
    }

    /**
     * Runs as the JVM shuts down: spawns a detached helper that waits for the
     * process (and its file locks) to disappear, then swaps each staged JAR in.
     */
    private static void applyOnExit() {
        List<Swap> pending;
        synchronized (swaps) { pending = new ArrayList<>(swaps); }
        if (pending.isEmpty()) return;

        Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath();
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");

        try {
            List<String> cmd = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            if (windows) {
                sb.append("ping 127.0.0.1 -n 3 > nul");
                for (Swap s : pending) {
                    sb.append(" & del /f /q \"").append(s.oldJar).append('"');
                    sb.append(" & move /y \"").append(s.stagedJar).append("\" \"")
                      .append(modsDir.resolve(s.finalName)).append('"');
                }
                cmd.add("cmd"); cmd.add("/c"); cmd.add(sb.toString());
            } else {
                sb.append("sleep 2");
                for (Swap s : pending) {
                    sb.append("; rm -f '").append(s.oldJar).append('\'');
                    sb.append("; mv '").append(s.stagedJar).append("' '")
                      .append(modsDir.resolve(s.finalName)).append('\'');
                }
                cmd.add("sh"); cmd.add("-c"); cmd.add(sb.toString());
            }
            new ProcessBuilder(cmd).start();
        } catch (Exception ignored) {
            // Nothing we can do during shutdown; staged files cleared next launch.
        }
    }
}
