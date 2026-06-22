package com.supermodmenu.icon;

import com.supermodmenu.SuperModMenuClient;
import com.supermodmenu.update.ModrinthUpdateChecker;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Loads and caches mod icons from:
 *  1. Mod JAR file (icon field in fabric.mod.json)
 *  2. Modrinth (fallback if not in JAR)
 *
 * Strategy:
 *  1. Check if mod has icon in its JAR (via ModContainer metadata)
 *  2. If not found, fetch from Modrinth
 *  3. Cache the Identifier for reuse
 */
public class ModIconCache {

    private static final String API_BASE = "https://api.modrinth.com/v2";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    // modId -> texture Identifier
    private static final Map<String, Identifier> cache = new ConcurrentHashMap<>();
    // modId -> future (to avoid duplicate requests)
    private static final Map<String, CompletableFuture<Identifier>> pending = new ConcurrentHashMap<>();

    /**
     * Get the cached icon Identifier for a mod, or null if not loaded yet.
     * Automatically starts loading in background if not already fetching.
     */
    public static Identifier getIcon(String modId) {
        if (cache.containsKey(modId)) {
            return cache.get(modId);
        }

        // Start fetching if not already pending
        if (!pending.containsKey(modId)) {
            fetchIconAsync(modId);
        }

        return null; // not ready yet
    }

    /**
     * Kick off an async icon fetch.
     */
    private static void fetchIconAsync(String modId) {
        CompletableFuture<Identifier> future = CompletableFuture.supplyAsync(() -> {
            try {
                return fetchIcon(modId);
            } catch (Exception e) {
                SuperModMenuClient.LOGGER.debug("Failed to fetch icon for {}: {}", modId, e.getMessage());
                return null;
            }
        });

        pending.put(modId, future);

        future.thenAccept(id -> {
            if (id != null) cache.put(modId, id);
            pending.remove(modId);
        });
    }

    private static Identifier fetchIcon(String modId) throws Exception {
        // Strategy 1: Try to load from mod JAR
        Identifier jarIcon = loadIconFromJar(modId);
        if (jarIcon != null) return jarIcon;

        // Strategy 2: Fetch from Modrinth
        return fetchIconFromModrinth(modId);
    }

    /**
     * Load icon from the mod's JAR file.
     */
    private static Identifier loadIconFromJar(String modId) {
        try {
            Optional<ModContainer> modOpt = FabricLoader.getInstance().getModContainer(modId);
            if (modOpt.isEmpty()) return null;

            ModContainer mod = modOpt.get();
            String iconPath = mod.getMetadata().getIconPath(32).orElse(null);
            
            if (iconPath == null) {
                // Try common icon paths
                iconPath = "assets/" + modId + "/icon.png";
            }

            // Load from mod's resources
            InputStream iconStream = mod.getClass().getClassLoader().getResourceAsStream(iconPath);
            if (iconStream == null) {
                // Try alternative: load from mod JAR path directly
                Path modPath = mod.getRootPaths().get(0);
                Path iconFile = modPath.resolve(iconPath);
                
                if (Files.exists(iconFile)) {
                    iconStream = Files.newInputStream(iconFile);
                }
            }

            if (iconStream != null) {
                try (InputStream is = iconStream) {
                    NativeImage image = NativeImage.read(is);
                    NativeImageBackedTexture texture = TextureCompat.create(image);

                    Identifier id = Identifier.of("supermodmenu", "icons/" + modId.replace(":", "_"));
                    MinecraftClient.getInstance().execute(() ->
                            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture)
                    );

                    SuperModMenuClient.LOGGER.debug("Loaded icon from JAR for: {}", modId);
                    return id;
                }
            }
        } catch (Exception e) {
            SuperModMenuClient.LOGGER.debug("Could not load icon from JAR for {}: {}", modId, e.getMessage());
        }

        return null;
    }

    /**
     * Fetch icon from Modrinth API.
     */
    private static Identifier fetchIconFromModrinth(String modId) {
        try {
            // Step 1: get projectId from update checker if available
            var result = ModrinthUpdateChecker.getResult(modId);
            String projectId = (result != null && result.projectId() != null)
                    ? result.projectId()
                    : modId; // fallback to mod ID as project ID

            // Step 2: fetch project metadata
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/project/" + projectId))
                    .header("User-Agent", SuperModMenuClient.USER_AGENT)
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;

            // Parse JSON to get icon_url
            com.google.gson.JsonObject project = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
            if (!project.has("icon_url")) return null;

            String iconUrl = project.get("icon_url").getAsString();
            if (iconUrl == null || iconUrl.isBlank()) return null;

            // NativeImage can't decode WebP (Modrinth's default icon format), so route
            // WebP through the wsrv.nl proxy which transcodes to PNG on the fly.
            String fetchUrl = iconUrl.toLowerCase().contains(".webp")
                    ? "https://wsrv.nl/?url=" + java.net.URLEncoder.encode(
                            iconUrl.replaceFirst("^https?://", ""),
                            java.nio.charset.StandardCharsets.UTF_8) + "&output=png"
                    : iconUrl;

            // Step 3: download icon image
            HttpRequest imgReq = HttpRequest.newBuilder()
                    .uri(URI.create(fetchUrl))
                    .header("User-Agent", SuperModMenuClient.USER_AGENT)
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<InputStream> imgResp = HTTP.send(imgReq, HttpResponse.BodyHandlers.ofInputStream());
            if (imgResp.statusCode() != 200) return null;

            // Step 4: load as NativeImage
            try (InputStream is = imgResp.body()) {
                NativeImage image = NativeImage.read(is);
                NativeImageBackedTexture texture = TextureCompat.create(image);

                // Register with Minecraft's texture manager
                Identifier id = Identifier.of("supermodmenu", "icons/" + modId.replace(":", "_") + "_mr");
                MinecraftClient.getInstance().execute(() ->
                        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture)
                );

                SuperModMenuClient.LOGGER.debug("Loaded icon from Modrinth for: {}", modId);
                return id;
            }
        } catch (Exception e) {
            SuperModMenuClient.LOGGER.debug("Could not fetch icon from Modrinth for {}: {}", modId, e.getMessage());
        }

        return null;
    }

    /**
     * Clear all cached icons (for cleanup or reload).
     */
    public static void clearCache() {
        cache.clear();
        pending.clear();
    }
}
