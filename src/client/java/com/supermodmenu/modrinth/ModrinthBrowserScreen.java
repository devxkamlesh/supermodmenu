package com.supermodmenu.modrinth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.supermodmenu.SuperModMenuClient;
import com.supermodmenu.gui.Theme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

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
 * "Get Mods" — browse and install Fabric mods from Modrinth.
 *
 * Opaque, widget-light screen: a header (title + search), a scrollable list of mod
 * cards drawn directly, and a footer. Each card has an Install button with a clear
 * state machine (Install → Downloading → Installed / Failed).
 */
public class ModrinthBrowserScreen extends Screen {

    private static final String API   = "https://api.modrinth.com/v2";
    private static final int PAGE     = 20;
    private static final int HEADER_H = 56;
    private static final int FOOTER_H = 34;
    private static final int CARD_H   = 60;
    private static final int CARD_GAP = 6;
    private static final int ICON     = 44;
    private static final int MAX_W    = 640;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private enum DlState { INSTALL, DOWNLOADING, DONE, FAILED }

    private final Screen parent;
    private TextFieldWidget searchBox;
    private ButtonWidget searchBtn, backBtn, loadMoreBtn;

    private final List<ModEntry> mods = new ArrayList<>();
    private final Set<String> installedSlugs = new HashSet<>();
    private final Map<String, DlState> dlState = new ConcurrentHashMap<>();

    private float scroll = 0, targetScroll = 0;
    private volatile boolean loading = false, hasMore = false;
    private volatile int currentOffset = 0;
    private volatile String lastQuery = null, statusMessage = null;

    public ModrinthBrowserScreen(Screen parent) {
        super(Text.literal("Get Mods"));
        this.parent = parent;
    }

    // ── Init ───────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        scanInstalledMods();

        int contentW = Math.min(MAX_W, width - 2 * Theme.PAD);
        int contentX = (width - contentW) / 2;
        int searchBtnW = 78;
        int searchW = contentW - searchBtnW - Theme.GAP;

        searchBox = new TextFieldWidget(textRenderer, contentX, 30, searchW, 20, Text.literal(""));
        searchBox.setMaxLength(128);
        searchBox.setPlaceholder(Text.literal("Search Modrinth…  (empty = popular)")
                .formatted(Formatting.DARK_GRAY));
        addDrawableChild(searchBox);

        searchBtn = ButtonWidget.builder(Text.literal("Search"),
                        b -> startSearch(searchBox.getText().trim()))
                .dimensions(contentX + searchW + Theme.GAP, 30, searchBtnW, 20).build();
        addDrawableChild(searchBtn);

        backBtn = ButtonWidget.builder(Text.literal("← Back"), b -> close())
                .dimensions(Theme.PAD, height - FOOTER_H + 7, 80, 20).build();
        addDrawableChild(backBtn);

        loadMoreBtn = ButtonWidget.builder(Text.literal("Load More ↓"), b -> loadMore())
                .dimensions(width - Theme.PAD - 110, height - FOOTER_H + 7, 110, 20).build();
        loadMoreBtn.visible = false;
        addDrawableChild(loadMoreBtn);

        if (mods.isEmpty() && statusMessage == null) startPopular();
    }

    // ── Installed detection ──────────────────────────────────────────────────--
    private void scanInstalledMods() {
        installedSlugs.clear();
        FabricLoader.getInstance().getAllMods().forEach(mc ->
                installedSlugs.add(mc.getMetadata().getId().toLowerCase()));
        try {
            Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
            if (Files.isDirectory(modsDir)) {
                try (var s = Files.list(modsDir)) {
                    s.filter(p -> p.toString().endsWith(".jar"))
                     .forEach(p -> installedSlugs.add(p.getFileName().toString().toLowerCase()
                             .replaceAll("-[0-9].*\\.jar$", "").replaceAll("\\.jar$", "")));
                }
            }
        } catch (Exception ignored) {}
    }

    private boolean isInstalled(ModEntry mod) {
        String slug = mod.slug.toLowerCase(), pid = mod.projectId.toLowerCase();
        return installedSlugs.contains(slug) || installedSlugs.contains(pid)
                || installedSlugs.stream().anyMatch(s -> s.contains(slug));
    }

    private DlState stateOf(ModEntry mod) {
        DlState s = dlState.get(mod.projectId);
        if (s != null) return s;
        return isInstalled(mod) ? DlState.DONE : DlState.INSTALL;
    }

    // ── Search / paging ──────────────────────────────────────────────────────--
    private void startPopular() {
        if (loading) return;
        lastQuery = null; currentOffset = 0; mods.clear();
        scroll = targetScroll = 0;
        fetchPage(null, 0, true);
    }

    private void startSearch(String query) {
        if (loading) return;
        if (query.isEmpty()) { startPopular(); return; }
        lastQuery = query; currentOffset = 0; mods.clear();
        scroll = targetScroll = 0;
        fetchPage(query, 0, true);
    }

    private void loadMore() {
        if (loading || !hasMore) return;
        fetchPage(lastQuery, currentOffset, false);
    }

    private void fetchPage(String query, int offset, boolean fresh) {
        loading = true; hasMore = false;
        statusMessage = fresh ? (query == null ? "Loading popular…" : "Searching…") : "Loading more…";
        client.execute(() -> { if (loadMoreBtn != null) loadMoreBtn.visible = false; });

        CompletableFuture.runAsync(() -> {
            try {
                // Loader + type only; the install step resolves a version for the game.
                String facets = URLEncoder.encode(
                        "[[\"categories:fabric\"],[\"project_type:mod\"]]", StandardCharsets.UTF_8);
                String url = (query == null)
                        ? API + "/search?facets=" + facets + "&limit=" + PAGE
                        + "&offset=" + offset + "&index=downloads"
                        : API + "/search?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&facets=" + facets + "&limit=" + PAGE + "&offset=" + offset;

                String body = getWithRetry(url);
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                JsonArray hits = root.getAsJsonArray("hits");
                int total = root.has("total_hits") ? root.get("total_hits").getAsInt() : 0;
                List<ModEntry> results = parseHits(hits);
                int newOff = offset + results.size();
                boolean more = !results.isEmpty() && newOff < total;

                client.execute(() -> {
                    if (fresh) mods.clear();
                    mods.addAll(results);
                    currentOffset = newOff; hasMore = more;
                    statusMessage = mods.isEmpty() ? "No mods found" : (mods.size() + " of " + total);
                    loading = false;
                    if (loadMoreBtn != null) loadMoreBtn.visible = more;
                });
            } catch (RateLimitedException rle) {
                statusMessage = "Error: Modrinth is rate-limiting (try again shortly)";
                loading = false;
            } catch (Exception e) {
                SuperModMenuClient.LOGGER.error("Modrinth fetch failed", e);
                statusMessage = "Error: " + e.getMessage(); loading = false;
            }
        });
    }

    private static final class RateLimitedException extends Exception {}

    private String getWithRetry(String url) throws Exception {
        for (int i = 0; i < 3; i++) {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .header("User-Agent", SuperModMenuClient.USER_AGENT).GET()
                    .timeout(Duration.ofSeconds(15)).build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            boolean throttled = resp.statusCode() == 429
                    || (body != null && (body.contains("error code: 1015")
                            || body.stripLeading().startsWith("<")));
            if (resp.statusCode() == 200 && !throttled) return body;
            if (!throttled) throw new RuntimeException("HTTP " + resp.statusCode());
            if (i < 2) {
                long wait = 2L + i * 2L;
                statusMessage = "Rate-limited — retrying in " + wait + "s…";
                try { Thread.sleep(wait * 1000L); } catch (InterruptedException ignored) {}
            }
        }
        throw new RateLimitedException();
    }

    private List<ModEntry> parseHits(JsonArray hits) {
        List<ModEntry> r = new ArrayList<>();
        if (hits == null) return r;
        for (int i = 0; i < hits.size(); i++) {
            JsonObject h = hits.get(i).getAsJsonObject();
            String iconUrl = (h.has("icon_url") && !h.get("icon_url").isJsonNull())
                    ? h.get("icon_url").getAsString() : null;
            ModEntry e = new ModEntry(
                    str(h, "slug"), str(h, "project_id"), str(h, "title"),
                    str(h, "description"), str(h, "author"),
                    h.has("downloads") ? h.get("downloads").getAsInt() : 0, iconUrl);
            r.add(e);
            if (iconUrl != null) ModrinthIconCache.getIcon(e.projectId, iconUrl);
        }
        return r;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    // ── Download (the proper install flow) ───────────────────────────────────--
    private void install(ModEntry mod) {
        if (stateOf(mod) == DlState.DOWNLOADING || stateOf(mod) == DlState.DONE) return;
        dlState.put(mod.projectId, DlState.DOWNLOADING);
        statusMessage = "Downloading " + mod.title + "…";

        CompletableFuture.runAsync(() -> {
            try {
                String mcVer = SharedConstants.getGameVersion().getName();
                // 1) Resolve a Fabric version compatible with this game version.
                String url = API + "/project/" + mod.projectId + "/version?loaders="
                        + URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8)
                        + "&game_versions=" + URLEncoder.encode("[\"" + mcVer + "\"]", StandardCharsets.UTF_8);
                String body = getWithRetry(url);
                JsonArray versions = JsonParser.parseString(body).getAsJsonArray();
                if (versions.isEmpty()) { failDl(mod, "No Fabric build for " + mcVer); return; }

                // 2) Pick the primary file of the newest version.
                JsonObject file = primaryFile(versions.get(0).getAsJsonObject().getAsJsonArray("files"));
                if (file == null) { failDl(mod, "No downloadable file"); return; }
                String dlUrl = file.get("url").getAsString();
                String fn = sanitize(file.get("filename").getAsString());
                if (!fn.endsWith(".jar")) { failDl(mod, "Unexpected file type"); return; }

                // 3) Download to a temp file, then move into mods/ atomically.
                Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
                Files.createDirectories(modsDir);
                Path tmp = modsDir.resolve(fn + ".part");
                Path dst = modsDir.resolve(fn);

                HttpRequest dlReq = HttpRequest.newBuilder().uri(URI.create(dlUrl))
                        .header("User-Agent", SuperModMenuClient.USER_AGENT).GET()
                        .timeout(Duration.ofSeconds(120)).build();
                HttpResponse<InputStream> dlResp = HTTP.send(dlReq, HttpResponse.BodyHandlers.ofInputStream());
                if (dlResp.statusCode() != 200) { failDl(mod, "Download HTTP " + dlResp.statusCode()); return; }
                try (InputStream in = dlResp.body()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);

                dlState.put(mod.projectId, DlState.DONE);
                installedSlugs.add(mod.slug.toLowerCase());
                statusMessage = "✔ Installed " + mod.title + " — restart to load";
            } catch (Exception e) {
                SuperModMenuClient.LOGGER.error("Install failed for {}", mod.slug, e);
                failDl(mod, e.getMessage());
            }
        });
    }

    private void failDl(ModEntry mod, String msg) {
        dlState.put(mod.projectId, DlState.FAILED);
        statusMessage = "Error: " + (msg == null ? "install failed" : msg);
    }

    private static JsonObject primaryFile(JsonArray files) {
        if (files == null || files.isEmpty()) return null;
        for (int i = 0; i < files.size(); i++) {
            JsonObject f = files.get(i).getAsJsonObject();
            if (f.has("primary") && f.get("primary").getAsBoolean()) return f;
        }
        return files.get(0).getAsJsonObject();
    }

    private static String sanitize(String name) { return name.replaceAll("[\\\\/:*?\"<>|]", "_"); }

    // ── Render (opaque, manual cards) ─────────────────────────────────────────--
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Suppress vanilla dirt background + copyright text; we paint our own.
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        scroll = Math.abs(targetScroll - scroll) > 0.5f
                ? MathHelper.lerp(0.35f, scroll, targetScroll) : targetScroll;
        if (mods.isEmpty() && !loading && statusMessage == null) startPopular();

        ctx.fill(0, 0, width, height, Theme.BG_APP);

        int contentW = Math.min(MAX_W, width - 2 * Theme.PAD);
        int contentX = (width - contentW) / 2;
        int listTop = HEADER_H + 6;
        int listBot = height - FOOTER_H;

        if (mods.isEmpty()) {
            drawCentreMessage(ctx, listTop, listBot, contentW);
        } else {
            ctx.enableScissor(0, listTop, width, listBot);
            int y = listTop - (int) scroll;
            for (ModEntry mod : mods) {
                if (y + CARD_H >= listTop && y <= listBot)
                    drawCard(ctx, contentX, y, contentW, mod, mouseX, mouseY);
                y += CARD_H + CARD_GAP;
            }
            ctx.disableScissor();
            drawScrollbar(ctx, listTop, listBot);
            if (loading) Theme.spinner(ctx, width / 2, listBot - 12, 7, Theme.GREEN);
        }

        // Header
        ctx.fill(0, 0, width, HEADER_H, Theme.BG_PANEL);
        Theme.divider(ctx, 0, HEADER_H, width);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("⬇ Get Mods").formatted(Formatting.BOLD), Theme.PAD, 8, Theme.GREEN);
        if (searchBtn != null) searchBtn.active = !loading;
        if (statusMessage != null) {
            int col = loading ? Theme.GOLD
                    : statusMessage.startsWith("✔") ? Theme.GREEN
                    : statusMessage.startsWith("Error") ? Theme.RED : Theme.TEXT_DIM;
            String s = Theme.clip(textRenderer, statusMessage, contentW - 90);
            ctx.drawTextWithShadow(textRenderer, Text.literal(s),
                    width - Theme.PAD - textRenderer.getWidth(s), 8, col);
        }

        // Footer
        ctx.fill(0, height - FOOTER_H, width, height, Theme.BG_PANEL);
        Theme.divider(ctx, 0, height - FOOTER_H, width);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Powered by Modrinth"),
                width / 2, height - FOOTER_H + 13, Theme.TEXT_DIM);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawCentreMessage(DrawContext ctx, int listTop, int listBot, int contentW) {
        int boxW = Math.min(380, contentW), boxX = (width - boxW) / 2;
        int boxY = (listTop + listBot) / 2 - 26, boxH = 52;
        Theme.panel(ctx, boxX, boxY, boxW, boxH, Theme.BG_PANEL, Theme.BORDER);
        if (loading) {
            Theme.spinner(ctx, width / 2, boxY + 16, 9, Theme.GREEN);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Loading mods" + Theme.workingDots()), width / 2, boxY + 34, Theme.TEXT);
        } else {
            boolean err = statusMessage != null && statusMessage.startsWith("Error");
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(err ? "⚠ Couldn't load mods" : "No mods found").formatted(Formatting.BOLD),
                    width / 2, boxY + 14, err ? Theme.RED : Theme.TEXT);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(err ? Theme.clip(textRenderer, statusMessage, boxW - 16)
                                     : "Try a different search."),
                    width / 2, boxY + 32, Theme.TEXT_DIM);
        }
    }

    private void drawCard(DrawContext ctx, int x, int y, int w, ModEntry mod, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + CARD_H && my >= HEADER_H;
        ctx.fill(x, y, x + w, y + CARD_H, hov ? Theme.BG_CARD_HOV : Theme.BG_CARD);
        ctx.drawBorder(x, y, w, CARD_H, Theme.BORDER);

        int iconX = x + 8, iconY = y + (CARD_H - ICON) / 2;
        Identifier id = mod.iconUrl != null ? ModrinthIconCache.getIcon(mod.projectId, mod.iconUrl) : null;
        if (id != null) {
            ctx.drawTexture(RenderLayer::getGuiTextured, id, iconX, iconY, 0, 0, ICON, ICON, ICON, ICON);
        } else {
            ctx.fill(iconX, iconY, iconX + ICON, iconY + ICON, Theme.BG_ELEVATED);
            ctx.drawBorder(iconX, iconY, ICON, ICON, Theme.BORDER_LIGHT);
            String letter = mod.title.isEmpty() ? "?" : mod.title.substring(0, 1).toUpperCase();
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(letter).formatted(Formatting.BOLD),
                    iconX + ICON / 2, iconY + (ICON - textRenderer.fontHeight) / 2, Theme.TEXT_DIM);
        }

        DlState st = stateOf(mod);
        int btnW = 96, btnH = 20, btnX = x + w - btnW - 8, btnY = y + (CARD_H - btnH) / 2;
        int textX = iconX + ICON + 10, maxW = btnX - textX - 8;

        ctx.drawTextWithShadow(textRenderer,
                Text.literal(Theme.clip(textRenderer, mod.title, maxW)).formatted(Formatting.BOLD),
                textX, y + 8, Theme.TEXT);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(Theme.clip(textRenderer,
                        "by " + mod.author + "  ·  " + fmt(mod.downloads) + " downloads", maxW)),
                textX, y + 20, Theme.TEXT_DIM);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(Theme.clip(textRenderer, mod.description, maxW)),
                textX, y + 34, Theme.TEXT_MUTED);

        // Install button
        boolean btnHov = mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH && my >= HEADER_H;
        int bg, border, fg = Theme.TEXT; String lbl;
        switch (st) {
            case DONE        -> { bg = 0xFF1A3A20; border = 0xFF2A6A2A; lbl = "✔ Installed"; }
            case DOWNLOADING -> { bg = Theme.BG_ELEVATED; border = Theme.BORDER; lbl = "↓ " + Theme.workingDots(); }
            case FAILED      -> { bg = 0xFF3A1E1E; border = Theme.RED; lbl = "⚠ Retry"; }
            default          -> { bg = btnHov ? Theme.GREEN : 0xFF26402C;
                                  border = btnHov ? Theme.GREEN : Theme.BORDER; lbl = "↓ Install"; }
        }
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
        ctx.drawBorder(btnX, btnY, btnW, btnH, border);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(lbl),
                btnX + btnW / 2, btnY + (btnH - textRenderer.fontHeight) / 2, fg);
    }

    private void drawScrollbar(DrawContext ctx, int listTop, int listBot) {
        int totalH = mods.size() * (CARD_H + CARD_GAP);
        int visibleH = listBot - listTop;
        if (totalH <= visibleH) return;
        int barX = width - 6, barW = 4;
        int thumbH = Math.max(24, visibleH * visibleH / totalH);
        float frac = scroll / (totalH - visibleH);
        int thumbY = listTop + (int) (frac * (visibleH - thumbH));
        ctx.fill(barX, listTop, barX + barW, listBot, 0x33FFFFFF);
        ctx.fill(barX, thumbY, barX + barW, thumbY + thumbH, Theme.BORDER_LIGHT);
    }

    private String fmt(int d) {
        return d >= 1_000_000 ? (d / 1_000_000) + "M" : d >= 1_000 ? (d / 1_000) + "K" : String.valueOf(d);
    }

    // ── Input ───────────────────────────────────────────────────────────────--
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            int contentW = Math.min(MAX_W, width - 2 * Theme.PAD);
            int contentX = (width - contentW) / 2;
            int listTop = HEADER_H + 6, listBot = height - FOOTER_H;
            if (my >= listTop && my <= listBot) {
                int y = listTop - (int) scroll;
                for (ModEntry mod : mods) {
                    int btnW = 96, btnH = 20, btnX = contentX + contentW - btnW - 8, btnY = y + (CARD_H - btnH) / 2;
                    if (mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH) {
                        DlState st = stateOf(mod);
                        if (st == DlState.INSTALL || st == DlState.FAILED) install(mod);
                        return true;
                    }
                    y += CARD_H + CARD_GAP;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        int totalH = mods.size() * (CARD_H + CARD_GAP);
        int visibleH = (height - FOOTER_H) - (HEADER_H + 6);
        int maxScroll = Math.max(0, totalH - visibleH);
        targetScroll = MathHelper.clamp(targetScroll - (float) (va * 32), 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256) { close(); return true; }
        if ((key == 257 || key == 335) && searchBox.isFocused()) {
            startSearch(searchBox.getText().trim()); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void close() { client.setScreen(parent); }

    @Override
    public void removed() { ModrinthIconCache.clearCache(); super.removed(); }

    record ModEntry(String slug, String projectId, String title, String description,
                    String author, int downloads, String iconUrl) {}
}
