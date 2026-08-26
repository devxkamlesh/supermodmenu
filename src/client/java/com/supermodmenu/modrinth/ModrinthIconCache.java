package com.supermodmenu.modrinth;

import com.supermodmenu.SuperModMenuClient;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Downloads and caches Modrinth project icons as GPU textures.
 *
 * Thread safety:
 *  - {@code cache}   : written only on the MC render thread (via execute()), read from any thread.
 *  - {@code pending} : used as a "already-fetching" guard; entries are removed once the
 *                      future completes so a retry is possible on transient failures.
 */
public class ModrinthIconCache {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    /** projectId → registered Identifier (only populated after texture is on the GPU) */
    private static final Map<String, Identifier> cache = new ConcurrentHashMap<>();

    /** projectId set of in-flight fetches (prevents duplicate requests) */
    private static final Set<String> pending = ConcurrentHashMap.newKeySet();

    /**
     * Returns the cached {@link Identifier} for the given project, or {@code null} if
     * the icon is not ready yet.  Kicks off an async fetch on the first call.
     */
    public static Identifier getIcon(String projectId, String iconUrl) {
        Identifier cached = cache.get(projectId);
        if (cached != null) return cached;

        if (iconUrl != null && !iconUrl.isEmpty() && pending.add(projectId)) {
            fetchAsync(projectId, iconUrl);
        }
        return null;
    }

    private static void fetchAsync(String projectId, String iconUrl) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(decodableUrl(iconUrl)))
                        .header("User-Agent", SuperModMenuClient.USER_AGENT)
                        .GET()
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<InputStream> resp =
                        HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());

                if (resp.statusCode() != 200) {
                    pending.remove(projectId);
                    return;
                }

                // NativeImage.read() must happen before we hand off to the GL thread
                // (InputStream will be closed once we leave this block)
                final NativeImage image;
                try (InputStream is = resp.body()) {
                    image = NativeImage.read(is);
                }

                // Texture registration MUST happen on the render thread
                Minecraft.getInstance().execute(() -> {
                    try {
                        Identifier id = Identifier.fromNamespaceAndPath("supermodmenu",
                                "modrinth_icon/" + projectId.toLowerCase());
                        DynamicTexture texture =
                                com.supermodmenu.icon.TextureCompat.create(image);
                        Minecraft.getInstance()
                                .getTextureManager()
                                .register(id, texture);
                        cache.put(projectId, id);
                    } catch (Throwable e) {
                        SuperModMenuClient.LOGGER.debug(
                                "Failed to register Modrinth icon texture for {}: {}",
                                projectId, e.getMessage());
                        image.close();
                    } finally {
                        pending.remove(projectId);
                    }
                });

            } catch (Exception e) {
                SuperModMenuClient.LOGGER.debug(
                        "Failed to fetch Modrinth icon for {}: {}", projectId, e.getMessage());
                pending.remove(projectId);
            }
        });
    }

    /**
     * Minecraft's {@link NativeImage#read} only decodes PNG/JPEG, but Modrinth serves
     * most project icons as WebP. We route WebP icons through the wsrv.nl image proxy
     * which transcodes them to PNG on the fly. PNG/JPEG icons are fetched directly.
     *
     * Note: this introduces a dependency on the public wsrv.nl service for WebP icons.
     * If it is unreachable the icon simply falls back to the letter placeholder.
     */
    private static String decodableUrl(String iconUrl) {
        if (iconUrl.toLowerCase().contains(".webp")) {
            String stripped = iconUrl.replaceFirst("^https?://", "");
            return "https://wsrv.nl/?url="
                    + URLEncoder.encode(stripped, StandardCharsets.UTF_8)
                    + "&output=png";
        }
        return iconUrl;
    }

    /** Unregisters all cached textures and clears state. Call when closing the screen. */
    public static void clearCache() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            cache.forEach((id, identifier) ->
                    mc.execute(() -> mc.getTextureManager().release(identifier)));
        }
        cache.clear();
        pending.clear();
    }
}
