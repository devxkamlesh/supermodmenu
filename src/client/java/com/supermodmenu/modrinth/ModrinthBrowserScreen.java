package com.supermodmenu.modrinth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.supermodmenu.SuperModMenuClient;
import com.supermodmenu.gui.Theme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

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
    private static final int HEADER_H = 64;
    private static final int FOOTER_H = 38;
    private static final int CARD_H   = 64;
    private static final int CARD_GAP = 8;
    private static final int ICON     = 44;
    private static final int MAX_W    = 640;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private enum DlState { INSTALL, DOWNLOADING, DONE, FAILED }

    private final Screen parent;
    private EditBox searchBox;
    private Button searchBtn, backBtn, loadMoreBtn;

    private final List<ModEntry> mods = new ArrayList<>();
    private final Set<String> installedSlugs = ConcurrentHashMap.newKeySet();
    private final Map<String, DlState> dlState = new ConcurrentHashMap<>();

    private float scroll = 0, targetScroll = 0;
    private volatile boolean loading = false, hasMore = false;
    private volatile int currentOffset = 0;
    private volatile String lastQuery = null, statusMessage = null;

    /** Session-wide flag: true if any mod was installed this session (signals the restart prompt). */
    public static volatile boolean newModsInstalled = false;

    public ModrinthBrowserScreen(Screen parent) {
        super(Component.literal("Get Mods"));
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

        searchBox = new EditBox(font, contentX, 36, searchW, 20, Component.literal(""));
        searchBox.setMaxLength(128);
        searchBox.setHint(Component.literal("Search Modrinth…  (empty = popular)")
            .withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(searchBox);

        searchBtn = Button.builder(Component.literal("Search"),
                b -> startSearch(searchBox.getValue().trim()))
            .bounds(contentX + searchW + Theme.GAP, 36, searchBtnW, 20).build();
        addRenderableWidget(searchBtn);

        backBtn = Button.builder(Component.literal("← Back"), b -> onClose())
            .bounds(Theme.PAD, height - FOOTER_H + 7, 80, 20).build();
        addRenderableWidget(backBtn);

        loadMoreBtn = Button.builder(Component.literal("Load More ↓"), b -> loadMore())
            .bounds(width - Theme.PAD - 110, height - FOOTER_H + 7, 110, 20).build();
        loadMoreBtn.visible = false;
        addRenderableWidget(loadMoreBtn);

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
        minecraft.execute(() -> { if (loadMoreBtn != null) loadMoreBtn.visible = false; });

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

                minecraft.execute(() -> {
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
                String mcVer = SharedConstants.getCurrentVersion().name();
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
                newModsInstalled = true;
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
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Suppress vanilla dirt background + copyright text; we paint our own.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        scroll = Math.abs(targetScroll - scroll) > 0.5f
                ? Mth.lerp(0.35f, scroll, targetScroll) : targetScroll;
        if (mods.isEmpty() && !loading && statusMessage == null) startPopular();

        ctx.fill(0, 0, width, height, Theme.BG_APP);

        int contentW = Math.min(MAX_W, width - 2 * Theme.PAD);
        int contentX = (width - contentW) / 2;
        int listTop = HEADER_H + 10;
        int listBot = height - FOOTER_H;

        ctx.fill(contentX - 8, HEADER_H + 4, contentX + contentW + 8, listBot,
            Theme.BG_SIDEBAR);
        ctx.outline(contentX - 8, HEADER_H + 4, contentW + 16,
            listBot - HEADER_H - 4, Theme.BORDER);

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
        ctx.fill(0, 0, width, HEADER_H, Theme.BG_HEADER);
        ctx.fill(0, 0, 4, HEADER_H, Theme.ACCENT);
        Theme.divider(ctx, 0, HEADER_H, width);
        ctx.text(font,
            Component.literal("GET MODS").withStyle(ChatFormatting.BOLD), Theme.PAD + 2, 8, Theme.TEXT, true);
        ctx.text(font, Component.literal("Discover Fabric mods from Modrinth"),
                Theme.PAD + 2, 21, Theme.TEXT_DIM, true);
        if (searchBtn != null) searchBtn.active = !loading;
        if (statusMessage != null) {
            int col = loading ? Theme.GOLD
                    : statusMessage.startsWith("✔") ? Theme.GREEN
                    : statusMessage.startsWith("Error") ? Theme.RED : Theme.TEXT_DIM;
                String s = Theme.clip(font, statusMessage, contentW - 90);
                ctx.text(font, Component.literal(s),
                    width - Theme.PAD - font.width(s), 8, col, true);
        }

        // Footer
        ctx.fill(0, height - FOOTER_H, width, height, Theme.BG_PANEL);
        Theme.divider(ctx, 0, height - FOOTER_H, width);
        ctx.centeredText(font, Component.literal("Powered by Modrinth"),
                width / 2, height - FOOTER_H + 13, Theme.TEXT_DIM);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void drawCentreMessage(GuiGraphicsExtractor ctx, int listTop, int listBot, int contentW) {
        int boxW = Math.min(380, contentW), boxX = (width - boxW) / 2;
        int boxY = (listTop + listBot) / 2 - 26, boxH = 52;
        Theme.accentedPanel(ctx, boxX, boxY, boxW, boxH,
            loading ? Theme.ACCENT : Theme.BLUE);
        if (loading) {
            Theme.spinner(ctx, width / 2, boxY + 16, 9, Theme.GREEN);
                ctx.centeredText(font,
                    Component.literal("Loading mods" + Theme.workingDots()), width / 2, boxY + 34, Theme.TEXT);
        } else {
            boolean err = statusMessage != null && statusMessage.startsWith("Error");
                ctx.centeredText(font,
                    Component.literal(err ? "⚠ Couldn't load mods" : "No mods found").withStyle(ChatFormatting.BOLD),
                    width / 2, boxY + 14, err ? Theme.RED : Theme.TEXT);
                ctx.centeredText(font,
                    Component.literal(err ? Theme.clip(font, statusMessage, boxW - 16)
                                     : "Try a different search."),
                    width / 2, boxY + 32, Theme.TEXT_DIM);
        }
    }

    private void drawCard(GuiGraphicsExtractor ctx, int x, int y, int w, ModEntry mod, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + CARD_H && my >= HEADER_H;
        ctx.fill(x, y, x + w, y + CARD_H, hov ? Theme.BG_CARD_HOV : Theme.BG_CARD);
        ctx.outline(x, y, w, CARD_H, hov ? Theme.BORDER_LIGHT : Theme.BORDER);
        if (hov) Theme.accentBar(ctx, x, y, CARD_H, Theme.ACCENT);

        int iconX = x + 8, iconY = y + (CARD_H - ICON) / 2;
        Identifier id = mod.iconUrl != null ? ModrinthIconCache.getIcon(mod.projectId, mod.iconUrl) : null;
        if (id != null) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, id, iconX, iconY, 0, 0, ICON, ICON, ICON, ICON);
        } else {
            ctx.fill(iconX, iconY, iconX + ICON, iconY + ICON, Theme.BG_ELEVATED);
            ctx.outline(iconX, iconY, ICON, ICON, Theme.BORDER_LIGHT);
            String letter = mod.title.isEmpty() ? "?" : mod.title.substring(0, 1).toUpperCase();
                ctx.centeredText(font, Component.literal(letter).withStyle(ChatFormatting.BOLD),
                    iconX + ICON / 2, iconY + (ICON - font.lineHeight) / 2, Theme.TEXT_DIM);
        }

        DlState st = stateOf(mod);
        int btnW = 96, btnH = 20, btnX = x + w - btnW - 8, btnY = y + (CARD_H - btnH) / 2;
        int textX = iconX + ICON + 10, maxW = btnX - textX - 8;

        ctx.text(font,
            Component.literal(Theme.clip(font, mod.title, maxW)).withStyle(ChatFormatting.BOLD),
            textX, y + 8, Theme.TEXT, true);
        ctx.text(font,
            Component.literal(Theme.clip(font,
                        "by " + mod.author + "  ·  " + fmt(mod.downloads) + " downloads", maxW)),
            textX, y + 20, Theme.TEXT_DIM, true);
        ctx.text(font,
            Component.literal(Theme.clip(font, mod.description, maxW)),
            textX, y + 34, Theme.TEXT_MUTED, true);

        // Install button
        boolean btnHov = mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH && my >= HEADER_H;
        int bg, border, fg = Theme.TEXT; String lbl;
        switch (st) {
            case DONE        -> { bg = Theme.ACCENT_DIM; border = Theme.ACCENT; lbl = "✔ Installed"; }
            case DOWNLOADING -> { bg = Theme.BG_ELEVATED; border = Theme.BORDER; lbl = "↓ " + Theme.workingDots(); }
            case FAILED      -> { bg = Theme.RED_DIM; border = Theme.RED; lbl = "⚠ Retry"; }
            default          -> { bg = btnHov ? Theme.ACCENT : Theme.ACCENT_DIM;
                                  border = btnHov ? Theme.ACCENT_HOVER : Theme.ACCENT; lbl = "↓ Install"; }
        }
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
        ctx.outline(btnX, btnY, btnW, btnH, border);
        ctx.centeredText(font, Component.literal(lbl),
            btnX + btnW / 2, btnY + (btnH - font.lineHeight) / 2, fg);
    }

    private void drawScrollbar(GuiGraphicsExtractor ctx, int listTop, int listBot) {
        int totalH = mods.size() * (CARD_H + CARD_GAP);
        int visibleH = listBot - listTop;
        if (totalH <= visibleH) return;
        int barX = width - 6, barW = 4;
        int thumbH = Math.max(24, visibleH * visibleH / totalH);
        float frac = scroll / (totalH - visibleH);
        int thumbY = listTop + (int) (frac * (visibleH - thumbH));
        ctx.fill(barX, listTop, barX + barW, listBot, Theme.BG_ELEVATED);
        ctx.fill(barX, thumbY, barX + barW, thumbY + thumbH, Theme.ACCENT);
    }

    private String fmt(int d) {
        return d >= 1_000_000 ? (d / 1_000_000) + "M" : d >= 1_000 ? (d / 1_000) + "K" : String.valueOf(d);
    }

    // ── Input ───────────────────────────────────────────────────────────────--
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (event.button() == 0) {
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
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        int totalH = mods.size() * (CARD_H + CARD_GAP);
        int visibleH = (height - FOOTER_H) - (HEADER_H + 10);
        int maxScroll = Math.max(0, totalH - visibleH);
        targetScroll = Mth.clamp(targetScroll - (float) (va * 32), 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { onClose(); return true; }
        if ((event.key() == 257 || event.key() == 335) && searchBox.isFocused()) {
            startSearch(searchBox.getValue().trim()); return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public void removed() { ModrinthIconCache.clearCache(); super.removed(); }

    record ModEntry(String slug, String projectId, String title, String description,
                    String author, int downloads, String iconUrl) {}
}
