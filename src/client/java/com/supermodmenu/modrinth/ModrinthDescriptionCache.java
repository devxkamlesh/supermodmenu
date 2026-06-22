package com.supermodmenu.modrinth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.supermodmenu.SuperModMenuClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches and caches rich mod descriptions + body text from Modrinth.
 * Cache persists for the session so repeated opens don't re-fetch.
 */
public class ModrinthDescriptionCache {

    private static final String API = "https://api.modrinth.com/v2";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    /** modId -> rich body (may be empty string if not found) */
    private static final Map<String, String> bodyCache  = new ConcurrentHashMap<>();
    /** modId -> short description (from /project search summary) */
    private static final Map<String, String> descCache  = new ConcurrentHashMap<>();
    /** modId -> project slug (for URL links) */
    private static final Map<String, String> slugCache  = new ConcurrentHashMap<>();

    private static final Set<String> pending = ConcurrentHashMap.newKeySet();

    /**
     * Returns the best available description for the given mod:
     * - Modrinth body text if already fetched
     * - Modrinth short description if body not yet available
     * - {@code fallback} if nothing is cached yet (fetch kicked off in background)
     */
    public static String getDescription(String modId, String fallback) {
        String body = bodyCache.get(modId);
        if (body != null) return body.isBlank() ? fallback : body;

        String desc = descCache.get(modId);

        // Kick off fetch if not already running
        if (pending.add(modId)) {
            fetchAsync(modId);
        }

        return (desc != null && !desc.isBlank()) ? desc : fallback;
    }

    /** Returns the Modrinth project slug, or null if not yet cached. */
    public static String getSlug(String modId) {
        return slugCache.get(modId);
    }

    private static void fetchAsync(String modId) {
        CompletableFuture.runAsync(() -> {
            try {
                // Search Modrinth by mod ID to get the project slug + short desc
                String searchUrl = API + "/search?query="
                        + URLEncoder.encode(modId, StandardCharsets.UTF_8)
                        + "&limit=5&project_type=mod";

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(searchUrl))
                        .header("User-Agent", SuperModMenuClient.USER_AGENT)
                        .GET().timeout(Duration.ofSeconds(10)).build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() != 200) { pending.remove(modId); return; }

                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                var hits = root.getAsJsonArray("hits");

                String slug = null;
                String shortDesc = null;

                // Find the hit whose slug or project_id matches our modId
                for (int i = 0; i < hits.size(); i++) {
                    JsonObject hit = hits.get(i).getAsJsonObject();
                    String hitSlug = hit.get("slug").getAsString();
                    String hitId   = hit.get("project_id").getAsString();
                    if (hitSlug.equalsIgnoreCase(modId) || hitId.equalsIgnoreCase(modId)
                            || hitSlug.replace("-","").equalsIgnoreCase(modId.replace("-",""))) {
                        slug      = hitSlug;
                        shortDesc = hit.has("description") ? hit.get("description").getAsString() : "";
                        break;
                    }
                }

                // If no exact match, use best guess (first result)
                if (slug == null && hits.size() > 0) {
                    JsonObject hit = hits.get(0).getAsJsonObject();
                    slug      = hit.get("slug").getAsString();
                    shortDesc = hit.has("description") ? hit.get("description").getAsString() : "";
                }

                if (shortDesc != null) descCache.put(modId, shortDesc);
                if (slug      != null) slugCache.put(modId, slug);

                // Now fetch the full project body
                if (slug != null) {
                    HttpRequest projReq = HttpRequest.newBuilder()
                            .uri(URI.create(API + "/project/" + slug))
                            .header("User-Agent", SuperModMenuClient.USER_AGENT)
                            .GET().timeout(Duration.ofSeconds(10)).build();
                    HttpResponse<String> projResp = HTTP.send(projReq, HttpResponse.BodyHandlers.ofString());

                    if (projResp.statusCode() == 200) {
                        JsonObject proj = JsonParser.parseString(projResp.body()).getAsJsonObject();
                        // 'body' is Markdown – strip common markdown for plain display
                        String body = proj.has("body") ? proj.get("body").getAsString() : "";
                        body = stripMarkdown(body);
                        bodyCache.put(modId, body);
                    }
                }

            } catch (Exception e) {
                SuperModMenuClient.LOGGER.debug("Failed to fetch Modrinth desc for {}: {}", modId, e.getMessage());
            } finally {
                pending.remove(modId);
            }
        });
    }

    /**
     * Very lightweight Markdown stripper for display in a Minecraft text renderer.
     * Removes headings, bold/italic markers, links, code blocks, images.
     */
    private static String stripMarkdown(String md) {
        if (md == null || md.isBlank()) return "";
        // Remove code blocks
        md = md.replaceAll("(?s)```.*?```", "");
        md = md.replaceAll("`[^`]*`", "");
        // Remove images  ![alt](url)
        md = md.replaceAll("!\\[[^]]*]\\([^)]*\\)", "");
        // Replace links [text](url) with just text
        md = md.replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1");
        // Remove headings
        md = md.replaceAll("(?m)^#{1,6}\\s*", "");
        // Remove bold/italic markers
        md = md.replaceAll("\\*{1,3}([^*]*)\\*{1,3}", "$1");
        md = md.replaceAll("_{1,3}([^_]*)_{1,3}", "$1");
        // Remove horizontal rules
        md = md.replaceAll("(?m)^[-*_]{3,}\\s*$", "");
        // Remove HTML tags
        md = md.replaceAll("<[^>]+>", "");
        // Collapse multiple blank lines into one
        md = md.replaceAll("(?m)(^\\s*$\\n){2,}", "\n");
        // Trim leading/trailing whitespace per line
        md = md.replaceAll("(?m)^\\s+", "");
        return md.strip();
    }

    public static void clearCache() {
        bodyCache.clear();
        descCache.clear();
        slugCache.clear();
        pending.clear();
    }
}
