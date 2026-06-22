package com.supermodmenu.update;

import com.google.gson.*;
import com.supermodmenu.SuperModMenuClient;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Checks for mod updates using the Modrinth API.
 *
 * Strategy (correct approach):
 *  1. For each mod, find its JAR file on disk
 *  2. Compute SHA-512 hash of the JAR bytes
 *  3. POST all hashes to POST /version_files/update with loader=fabric + game_version
 *  4. Response maps hash -> latest version info
 *  5. Compare version_number to installed version
 *
 * API: https://docs.modrinth.com/api/operations/getlatestversionsfromhashes
 */
public class ModrinthUpdateChecker {

    private static final String API_BASE = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = SuperModMenuClient.USER_AGENT;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // modId -> result
    private static final Map<String, UpdateResult> results = new ConcurrentHashMap<>();
    // sha512hex -> modId  (reverse lookup)
    private static final Map<String, String> hashToModId = new ConcurrentHashMap<>();

    private static volatile boolean checking = false;
    private static volatile String statusMessage = "";

    public static boolean isChecking()       { return checking; }
    public static String  getStatusMessage() { return statusMessage; }

    public static Map<String, UpdateResult> getResults() {
        return Collections.unmodifiableMap(results);
    }

    public static UpdateResult getResult(String modId) {
        return results.getOrDefault(modId, UpdateResult.UNKNOWN);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static CompletableFuture<Void> checkAllAsync(String minecraftVersion) {
        if (checking) return CompletableFuture.completedFuture(null);
        checking = true;
        results.clear();
        hashToModId.clear();
        statusMessage = "Hashing mod files...";

        return CompletableFuture.runAsync(() -> {
            try {
                checkAll(minecraftVersion);
                statusMessage = "Done";
            } catch (Exception e) {
                SuperModMenuClient.LOGGER.error("Update check failed", e);
                statusMessage = "Error: " + e.getMessage();
            } finally {
                checking = false;
            }
        });
    }

    // ── Core logic ────────────────────────────────────────────────────────────

    private static void checkAll(String minecraftVersion) throws Exception {
        // Step 1: collect mod JARs and compute SHA-512 hashes
        Map<String, String> hashToId = new LinkedHashMap<>(); // sha512 -> modId

        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String modId = mod.getMetadata().getId();

            // Skip virtual/builtin mods (no real JAR)
            List<Path> paths = mod.getOrigin().getPaths();
            if (paths.isEmpty()) continue;

            Path jarPath = paths.get(0);
            if (!Files.isRegularFile(jarPath)) continue;
            if (!jarPath.toString().endsWith(".jar")) continue;

            try {
                String hash = sha512hex(jarPath);
                hashToId.put(hash, modId);
                hashToModId.put(hash, modId);
            } catch (Exception e) {
                SuperModMenuClient.LOGGER.warn("Could not hash {}: {}", modId, e.getMessage());
            }
        }

        if (hashToId.isEmpty()) {
            statusMessage = "No hashable mods found";
            return;
        }

        statusMessage = "Querying Modrinth for " + hashToId.size() + " mods...";
        SuperModMenuClient.LOGGER.info("[SuperModMenu] Checking {} mods via Modrinth hash API", hashToId.size());

        // Step 2: POST /version_files/update
        // Body: { "hashes": [...], "algorithm": "sha512", "loaders": ["fabric"], "game_versions": ["1.21.4"] }
        JsonObject body = new JsonObject();
        JsonArray hashes = new JsonArray();
        hashToId.keySet().forEach(hashes::add);
        body.add("hashes", hashes);
        body.addProperty("algorithm", "sha512");

        JsonArray loaders = new JsonArray();
        loaders.add("fabric");
        body.add("loaders", loaders);

        JsonArray gameVersions = new JsonArray();
        gameVersions.add(minecraftVersion);
        body.add("game_versions", gameVersions);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/version_files/update"))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(Duration.ofSeconds(20))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            SuperModMenuClient.LOGGER.warn("[SuperModMenu] Modrinth returned HTTP {}: {}", response.statusCode(), response.body());
            statusMessage = "Modrinth API error: HTTP " + response.statusCode();
            return;
        }

        // Step 3: parse response — it's a map of hash -> version object
        JsonObject responseObj = JsonParser.parseString(response.body()).getAsJsonObject();
        int updatesFound = 0;

        // Mark all hashed mods as UP_TO_DATE first, then override with updates
        for (String modId : hashToId.values()) {
            results.put(modId, UpdateResult.UP_TO_DATE);
        }

        for (Map.Entry<String, JsonElement> entry : responseObj.entrySet()) {
            String hash = entry.getKey();
            String modId = hashToId.get(hash);
            if (modId == null) continue;

            JsonObject versionObj = entry.getValue().getAsJsonObject();
            String latestVersionNumber = versionObj.get("version_number").getAsString();
            String projectId = versionObj.get("project_id").getAsString();

            // Get installed version
            ModContainer mod = FabricLoader.getInstance().getModContainer(modId).orElse(null);
            if (mod == null) continue;
            String installedVersion = mod.getMetadata().getVersion().getFriendlyString();

            // Get project slug for the URL
            String projectSlug = fetchProjectSlug(projectId);
            String url = projectSlug != null
                    ? "https://modrinth.com/mod/" + projectSlug
                    : "https://modrinth.com/project/" + projectId;

            if (!latestVersionNumber.equals(installedVersion)) {
                results.put(modId, new UpdateResult(
                        Status.UPDATE_AVAILABLE,
                        installedVersion,
                        latestVersionNumber,
                        url,
                        projectId
                ));
                updatesFound++;
                SuperModMenuClient.LOGGER.info("[SuperModMenu] Update: {} {} -> {}", modId, installedVersion, latestVersionNumber);
            }
        }

        statusMessage = updatesFound > 0
                ? updatesFound + " update(s) available"
                : "All mods up to date";

        SuperModMenuClient.LOGGER.info("[SuperModMenu] Update check complete: {}", statusMessage);
    }

    private static String fetchProjectSlug(String projectId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/project/" + projectId))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .timeout(Duration.ofSeconds(8))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (obj.has("slug")) return obj.get("slug").getAsString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ── SHA-512 ───────────────────────────────────────────────────────────────

    private static String sha512hex(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder(128);
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ── Types ─────────────────────────────────────────────────────────────────

    public enum Status { UNKNOWN, UP_TO_DATE, UPDATE_AVAILABLE }

    public record UpdateResult(
            Status status,
            String currentVersion,
            String latestVersion,
            String url,
            String projectId
    ) {
        public static final UpdateResult UNKNOWN    = new UpdateResult(Status.UNKNOWN, null, null, null, null);
        public static final UpdateResult UP_TO_DATE = new UpdateResult(Status.UP_TO_DATE, null, null, null, null);
    }
}
