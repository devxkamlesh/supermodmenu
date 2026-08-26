package com.supermodmenu.gui;

import com.supermodmenu.SuperModMenuClient;
import com.supermodmenu.data.ModDataManager;
import com.supermodmenu.icon.ModIconCache;
import com.supermodmenu.modrinth.ModrinthBrowserScreen;
import com.supermodmenu.modrinth.ModrinthDescriptionCache;
import com.supermodmenu.update.ModrinthUpdateChecker;
import com.supermodmenu.dependency.DependencyGraph;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main Super Mod Menu screen — a responsive two-pane shell.
 *
 *   ┌──────────── header (title · count · search · filters) ─────────────┐
 *   │ sidebar (mod list)        │  detail panel (info + scrollable bio)   │
 *   │                           │  action grid (only when a mod selected) │
 *   └──────────── footer (Get Mods · Updates · Done) ────────────────────┘
 *
 * Only top-level, user-installed mods are listed; bundled libraries and nested
 * (jar-in-jar) modules are hidden so the list reflects what you'd actually
 * download and manage.
 */
public class ModListScreen extends Screen {

    private int sidebarW, headerH, footerH, listTop, rightX, rightW, actionTop;

    private final Screen parent;

    private EditBox searchBox;
    private ModListWidget   list;

    private Button favBtn, noteBtn, depsBtn, configBtn, modrinthBtn, updateBtn;
    private Button getModsBtn, updatesBtn, updatesBadge, restartBtn;
    private Button linkWebBtn, linkSrcBtn, linkIssuesBtn;
    private String curHome, curSrc, curIssues;
    private int linkRowY;
    private final List<Button> filterTabs = new ArrayList<>();

    private DependencyGraph graph;
    private int gridColW, gridC2, gridR2, gridGap;   // geometry reused to reflow the action grid

    private List<ModContainer> allMods      = new ArrayList<>();
    private List<ModContainer> filteredMods = new ArrayList<>();
    private ModContainer       selected;
    private FilterMode         filter       = FilterMode.ALL;
    private int                detailScroll = 0;

    public enum FilterMode {
        ALL("All"), FAVORITES("Favorites"), HAS_UPDATE("Updates");
        public final String label;
        FilterMode(String label) { this.label = label; }
    }

    public ModListScreen(Screen parent) {
        super(Component.literal("Mod Menu Pro"));
        this.parent = parent;
    }

    // ── Init / layout ────────────────────────────────────────────────────────
    @Override
    protected void init() {
        allMods = FabricLoader.getInstance().getAllMods().stream()
                .filter(m -> !isLibrary(m))
                .collect(Collectors.toList());
        graph = DependencyGraph.build();

        // Responsive split: sidebar is ~36% but always leaves a usable detail pane,
        // even at high GUI scale / small windows (effective width can be ~320).
        int desiredSidebar = Math.max(180, Math.min(320, (int) (width * 0.36)));
        sidebarW = Math.min(desiredSidebar, Math.max(140, width - 210));
        headerH  = 48;
        footerH  = 38;
        listTop  = headerH + 56;

        rightX = sidebarW + Theme.PAD;
        rightW = width - rightX - Theme.PAD;

        int gap = 5;
        int gridH = 3 * Theme.BTN_H + 2 * gap;
        actionTop = height - footerH - gridH - Theme.PAD;

        // Search
        searchBox = new EditBox(font,
            Theme.PAD, headerH + 10, sidebarW - 2 * Theme.PAD, 20, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search mods…").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setResponder(s -> refreshFilter());
        addRenderableWidget(searchBox);

        // Filter segmented control
        int tabsY = headerH + 34, tabGap = 4;
        int tabW = (sidebarW - 2 * Theme.PAD - tabGap * (FilterMode.values().length - 1))
                / FilterMode.values().length;
        int tabX = Theme.PAD;
        filterTabs.clear();
        for (FilterMode mode : FilterMode.values()) {
            FilterMode m = mode;
            Button tab = Button.builder(Component.literal(mode.label),
                            b -> { filter = m; refreshFilter(); })
                        .bounds(tabX, tabsY, tabW, 16).build();
                    addRenderableWidget(tab);
            filterTabs.add(tab);
            tabX += tabW + tabGap;
        }

        // Mod list
        int listH = (height - footerH) - listTop;
        list = new ModListWidget(minecraft, sidebarW, listH, listTop, 36, this);
        addRenderableWidget(list);

        // Detail action grid (2 cols × 3 rows)
        int colW = (rightW - gap) / 2;
        int c1 = rightX, c2 = rightX + colW + gap;
        int r0 = actionTop, r1 = r0 + Theme.BTN_H + gap, r2 = r1 + Theme.BTN_H + gap;
        gridColW = colW; gridC2 = c2; gridR2 = r2; gridGap = gap;

        favBtn      = action("★ Favorite",  c1, r0, colW, this::toggleFavorite);
        noteBtn     = action("✎ Note",       c2, r0, colW, this::openNote);
        depsBtn     = action("Dependencies", c1, r1, colW, this::openDeps);
        configBtn   = action("⚙ Configure",  c2, r1, colW, this::openConfig);
        // Row 3 holds Modrinth (full width), or Update + Modrinth side-by-side when
        // an update is available — repositioned in updateActionButtons().
        updateBtn   = action("⬆ Update",     c1, r2, colW, this::installUpdate);
        modrinthBtn = action("↗ Open on Modrinth", c1, r2, rightW, this::openModrinth);

        // Header: clickable "N updates" badge (jumps to the Updates filter)
        updatesBadge = Button.builder(Component.literal("⬆ updates"),
                        b -> { filter = FilterMode.HAS_UPDATE; refreshFilter(); })
                .bounds(width - 130, 6, 120, 16).build();
            addRenderableWidget(updatesBadge);

        // Header: "Restart to apply" — shown after a mod is installed/updated this session.
        restartBtn = Button.builder(Component.literal("⟳ Restart to apply"), b -> promptRestart())
            .bounds(width - 154, 24, 144, 16).build();
        restartBtn.visible = false;
        addRenderableWidget(restartBtn);

        // Detail header link buttons (Website / Source / Issues), Mod Menu style.
        linkRowY = headerH + Theme.PAD + 4 + 46 + 12;
        linkWebBtn    = linkButton("Website", () -> openUrl(curHome));
        linkSrcBtn    = linkButton("Source",  () -> openUrl(curSrc));
        linkIssuesBtn = linkButton("Issues",  () -> openUrl(curIssues));

        // Footer — responsive: 3 buttons share the space left of a right-pinned Done.
        int fy = height - footerH + 7;
        int doneW = 60;
        int doneX = width - Theme.PAD - doneW;
        int leftAvail = doneX - Theme.PAD - Theme.GAP;
        int bw = Math.max(54, Math.min(108, (leftAvail - 2 * Theme.GAP) / 3));
        int fx = Theme.PAD;
        getModsBtn = footer("⬇ Get Mods", fx, fy, bw,
                b -> minecraft.gui.setScreen(new ModrinthBrowserScreen(this)));
        fx += bw + Theme.GAP;
        updatesBtn = footer("⟳ Updates", fx, fy, bw, b -> checkUpdates());
        fx += bw + Theme.GAP;
        footer("\uD83D\uDCC1 Folder", fx, fy, bw, b -> openModsFolder());
        footer("Done", doneX, fy, doneW, b -> onClose());

        updateActionButtons();
        refreshFilter();
    }

    private Button action(String label, int x, int y, int w, Runnable onClick) {
        Button b = Button.builder(Component.literal(label), btn -> onClick.run())
                .bounds(x, y, w, Theme.BTN_H).build();
        return addRenderableWidget(b);
    }

    private Button linkButton(String label, Runnable onClick) {
        Button b = Button.builder(Component.literal(label), btn -> onClick.run())
                .bounds(rightX, linkRowY, 60, 16).build();
        b.visible = false;
        return addRenderableWidget(b);
    }

    private void openUrl(String url) {
        if (url == null || url.isBlank()) return;
        try {
            net.minecraft.util.Util.getPlatform().openUri(java.net.URI.create(url));
        } catch (Exception e) {
            SuperModMenuClient.LOGGER.error("Failed to open URL {}", url, e);
        }
    }

    /** Position the visible Website/Source/Issues buttons in a left-to-right row. */
    private void layoutLinks() {
        int lx = rightX;
        Button[] btns = {linkWebBtn, linkSrcBtn, linkIssuesBtn};
        String[] urls = {curHome, curSrc, curIssues};
        for (int i = 0; i < btns.length; i++) {
            Button b = btns[i];
            if (b == null) continue;
            boolean show = selected != null && urls[i] != null && !urls[i].isBlank();
            if (show) {
                int w = font.width(b.getMessage().getString()) + 14;
                if (lx + w > rightX + rightW) { b.visible = false; continue; }  // would overflow
                b.visible = true;
                b.setX(lx);
                b.setY(linkRowY);
                b.setWidth(w);
                lx += w + Theme.GAP;
            } else {
                b.visible = false;
            }
        }
    }

    private Button footer(String label, int x, int y, int w, Button.OnPress onClick) {
        return addRenderableWidget(Button.builder(Component.literal(label), onClick)
                .bounds(x, y, w, 20).build());
    }

    // ── Library detection ────────────────────────────────────────────────────
    /**
     * A mod is hidden from the list if it is the runtime itself or a bundled
     * library/module rather than something the user installed directly:
     *   - {@code builtin} pseudo-mods (minecraft, java, …)
     *   - nested jar-in-jar modules (Fabric API sub-modules, shaded libraries)
     */
    private boolean isLibrary(ModContainer mod) {
        if ("builtin".equals(mod.getMetadata().getType())) return true;
        if (mod.getContainingMod().isPresent()) return true;
        try {
            return mod.getOrigin().getKind() == ModOrigin.Kind.NESTED;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Filtering ──────────────────────────────────────────────────────────--
    public void refreshFilter() {
        String q = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT).trim();

        filteredMods = allMods.stream()
                .filter(m -> {
                    if (q.isEmpty()) return true;
                    return m.getMetadata().getName().toLowerCase().contains(q)
                            || m.getMetadata().getId().toLowerCase().contains(q);
                })
                .filter(m -> switch (filter) {
                    case FAVORITES  -> ModDataManager.isFavorite(m.getMetadata().getId());
                    case HAS_UPDATE -> ModrinthUpdateChecker.getResult(m.getMetadata().getId()).status()
                            == ModrinthUpdateChecker.Status.UPDATE_AVAILABLE;
                    default         -> true;
                })
                .sorted((a, b) -> {
                    boolean fa = ModDataManager.isFavorite(a.getMetadata().getId());
                    boolean fb = ModDataManager.isFavorite(b.getMetadata().getId());
                    if (fa != fb) return fa ? -1 : 1;
                    return a.getMetadata().getName().compareToIgnoreCase(b.getMetadata().getName());
                })
                .collect(Collectors.toList());

        if (list != null) list.setMods(filteredMods);
    }

    public void selectMod(ModContainer mod) {
        this.selected = mod;
        this.detailScroll = 0;
        updateActionButtons();
    }

    public ModContainer getSelectedMod() { return selected; }

    private void updateActionButtons() {
        boolean has = selected != null;
        Button[] all = {favBtn, noteBtn, depsBtn, configBtn, modrinthBtn, updateBtn};
        if (!has) {
            for (Button b : all) if (b != null) b.visible = false;
            curHome = curSrc = curIssues = null;
            layoutLinks();
            return;
        }

        String id = selected.getMetadata().getId();
        var contact = selected.getMetadata().getContact();
        curHome   = contact.get("homepage").orElse(null);
        curSrc    = contact.get("sources").orElse(null);
        curIssues = contact.get("issues").orElse(null);
        layoutLinks();
        boolean hasUpdate = ModrinthUpdateChecker.getResult(id).status()
                == ModrinthUpdateChecker.Status.UPDATE_AVAILABLE;
        boolean hasConfig = hasConfigScreen(selected);

        favBtn.setMessage(Component.literal(
                ModDataManager.isFavorite(id) ? "★ Unfavorite" : "☆ Favorite"));
        if (hasUpdate) refreshUpdateButtonLabel();

        // Build the ordered list of buttons that are actually shown, then flow them
        // into a 2-column grid (so the common case is a tidy 2×2).
        List<Button> vis = new ArrayList<>();
        vis.add(favBtn);
        vis.add(noteBtn);
        if (hasUpdate) vis.add(updateBtn);
        vis.add(depsBtn);
        if (hasConfig) vis.add(configBtn);
        vis.add(modrinthBtn);

        for (Button b : all) if (b != null) b.visible = false;
        for (int i = 0; i < vis.size(); i++) {
            Button b = vis.get(i);
            int col = i % 2, row = i / 2;
            b.setX(col == 0 ? rightX : gridC2);
            b.setY(actionTop + row * (Theme.BTN_H + gridGap));
            b.setWidth(gridColW);
            b.visible = true;
        }
    }

    private void refreshUpdateButtonLabel() {
        if (updateBtn == null || selected == null) return;
        // The update button now opens the Modrinth page for the new version.
        updateBtn.setMessage(Component.literal("⬆ Update on Modrinth"));
        updateBtn.active = true;
    }

    // ── Render ─────────────────────────────────────────────────────────────--
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Suppress vanilla dirt/blur; we paint a solid background for crisp, readable text.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Solid, opaque surfaces — high contrast, no blurry/gray wash.
        ctx.fill(0, 0, width, height, Theme.BG_APP);
        ctx.fill(0, headerH, sidebarW, height - footerH, Theme.BG_SIDEBAR);
        ctx.fill(sidebarW, headerH, sidebarW + 1, height - footerH, Theme.BORDER);
        ctx.fill(0, height - footerH, width, height, Theme.BG_PANEL);
        Theme.panel(ctx, rightX - 4, headerH + 8, rightW + 8, (actionTop - 12) - (headerH + 8),
            Theme.BG_PANEL, Theme.BORDER);
        if (selected != null) {
            Theme.panel(ctx, rightX - 4, actionTop - 5, rightW + 8,
                height - footerH - actionTop, Theme.BG_PANEL, Theme.BORDER);
            Theme.sectionLabel(ctx, font, "Actions", rightX, actionTop - 17, Theme.TEXT_DIM);
        }

        // Keep the live update button label in sync while a download runs.
        if (updateBtn != null && updateBtn.visible) refreshUpdateButtonLabel();

        // Update badge (top-right): visible only when updates exist.
        int updateCount = (int) allMods.stream().filter(m ->
                ModrinthUpdateChecker.getResult(m.getMetadata().getId()).status()
                        == ModrinthUpdateChecker.Status.UPDATE_AVAILABLE).count();
        if (updatesBadge != null) {
            updatesBadge.visible = updateCount > 0;
            updatesBadge.setMessage(Component.literal("⬆ " + updateCount + " update"
                    + (updateCount == 1 ? "" : "s")));
        }
        if (restartBtn != null) {
            restartBtn.visible = ModrinthBrowserScreen.newModsInstalled;
        }

        drawHeader(ctx);

        if (restartBtn != null && restartBtn.visible)
                ctx.fill(restartBtn.getX() - 1, restartBtn.getY() - 1,
                    restartBtn.getX() + restartBtn.getWidth(),
                    restartBtn.getY() + restartBtn.getHeight(), Theme.ACCENT_DIM);

        if (getModsBtn != null)
                ctx.fill(getModsBtn.getX() - 1, getModsBtn.getY() - 1,
                    getModsBtn.getX() + getModsBtn.getWidth(),
                    getModsBtn.getY() + getModsBtn.getHeight(), Theme.ACCENT_DIM);
        if (updatesBtn != null)
            ctx.fill(updatesBtn.getX(), updatesBtn.getY(),
                    updatesBtn.getX() + updatesBtn.getWidth(),
                    updatesBtn.getY() + updatesBtn.getHeight(), Theme.BLUE_DIM);

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // Active filter tab underline
        FilterMode[] vals = FilterMode.values();
        for (int i = 0; i < filterTabs.size(); i++) {
            if (vals[i] != filter) continue;
            Button t = filterTabs.get(i);
            ctx.fill(t.getX(), t.getY() + t.getHeight(),
                    t.getX() + t.getWidth(), t.getY() + t.getHeight() + 2, Theme.ACCENT);
        }

        drawDetail(ctx);

        Theme.divider(ctx, 0, height - footerH, width);
        ctx.centeredText(font,
            Component.literal(com.supermodmenu.Attribution.credit()),
                width / 2, height - footerH + 13, Theme.TEXT_DIM);
        if (ModrinthUpdateChecker.isChecking()) {
                ctx.text(font,
                    Component.literal("⟳ " + ModrinthUpdateChecker.getStatusMessage())
                        .withStyle(ChatFormatting.YELLOW),
                    rightX, height - footerH - 12, Theme.TEXT, true);
        }
    }

    private void drawHeader(GuiGraphicsExtractor ctx) {
        ctx.fill(0, 0, width, headerH, Theme.BG_HEADER);
        ctx.fill(0, 0, 4, headerH, Theme.ACCENT);
        Theme.divider(ctx, 0, headerH, width);

        ctx.text(font,
                Component.literal("MOD MENU PRO").withStyle(ChatFormatting.BOLD),
            Theme.PAD + 2, 9, Theme.TEXT, true);
        ctx.text(font,
            Component.literal("Library  /  " + allMods.size() + " installed  /  " + filteredMods.size() + " shown"),
            Theme.PAD + 2, 25, Theme.TEXT_DIM, true);
    }

    private void drawDetail(GuiGraphicsExtractor ctx) {
        int x = rightX;
        int top = headerH + Theme.PAD + 4;
        int bottom = actionTop - Theme.PAD;

        if (selected == null) {
            int boxW = Math.min(280, rightW - 24);
            int boxX = rightX + (rightW - boxW) / 2;
            Theme.accentedPanel(ctx, boxX, top + 18, boxW, 62, Theme.ACCENT);
            ctx.centeredText(font,
                Component.literal("Select a mod").withStyle(ChatFormatting.BOLD),
                rightX + rightW / 2, top + 38, Theme.TEXT);
            ctx.centeredText(font,
                Component.literal("Details and management tools appear here"),
                rightX + rightW / 2, top + 55, Theme.TEXT_DIM);
            return;
        }

        var meta = selected.getMetadata();
        int y = top;

        Theme.sectionLabel(ctx, font, "Overview", x, y, Theme.ACCENT);
        y += 16;

        int iconSz = 46;
        Identifier icon = ModIconCache.getIcon(meta.getId());
        if (icon != null) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0, 0,
                    iconSz, iconSz, iconSz, iconSz);
        } else {
            Theme.panel(ctx, x, y, iconSz, iconSz, Theme.BG_ELEVATED, Theme.BORDER_LIGHT);
            String fl = meta.getName().isEmpty() ? "?" : meta.getName().substring(0, 1).toUpperCase();
                ctx.centeredText(font, Component.literal(fl).withStyle(ChatFormatting.BOLD),
                    x + iconSz / 2, y + (iconSz - font.lineHeight) / 2, Theme.TEXT_DIM);
        }

        int tx = x + iconSz + 10, tw = rightW - iconSz - 10;
        ModEnvironment env = meta.getEnvironment();
        String envLabel = env == ModEnvironment.CLIENT ? "Client"
                : env == ModEnvironment.SERVER ? "Server" : null;
        int envColor = env == ModEnvironment.SERVER ? 0xFF6B541D : 0xFF244F78;

        // Name (bold, full width). The badge goes on the version line below so it can't
        // overlap the (wider-than-measured) bold name.
        ctx.text(font,
            Component.literal(Theme.clip(font, meta.getName(), tw)).withStyle(ChatFormatting.BOLD),
            tx, y + 2, Theme.TEXT, true);

        // Version + environment badge on the same line
        String verText = "v" + Theme.shortVersion(meta.getVersion().getFriendlyString());
        ctx.text(font, Component.literal(verText), tx, y + 15, Theme.TEXT_MUTED, true);
        if (envLabel != null) {
                Theme.envBadge(ctx, font, envLabel,
                    tx + font.width(verText) + 6, y + 13, envColor);
        }
        String authors = meta.getAuthors().stream().map(p -> p.getName()).collect(Collectors.joining(", "));
        if (!authors.isBlank()) {
                ctx.text(font,
                    Component.literal(Theme.clip(font, "By " + authors, tw)), tx, y + 26, Theme.TEXT_DIM, true);
        }

        if (ModDataManager.isFavorite(meta.getId())) {
            ctx.text(font, Component.literal("★"), x + iconSz - 8, y - 2, Theme.GOLD, true);
        }

        y += iconSz + 8;

        // Reserve the link-button row (Website / Source / Issues) drawn as widgets.
        boolean hasLinks = (curHome != null && !curHome.isBlank())
                || (curSrc != null && !curSrc.isBlank())
                || (curIssues != null && !curIssues.isBlank());
        if (hasLinks) y = linkRowY + 20;

        var upd = ModrinthUpdateChecker.getResult(meta.getId());
        if (upd.status() == ModrinthUpdateChecker.Status.UPDATE_AVAILABLE) {
            Theme.banner(ctx, font, "⬆ Update available: " + upd.latestVersion(),
                    x, y, rightW, Theme.GREEN, Theme.GREEN_DIM);
            y += 22;
        }
        String note = ModDataManager.getNote(meta.getId());
        if (!note.isBlank()) {
            Theme.banner(ctx, font, Theme.clip(font, "✎ " + note, rightW - 12),
                    x, y, rightW, Theme.ACCENT, Theme.ACCENT_DIM);
            y += 22;
        }

        Theme.divider(ctx, x, y, rightW);
        y += 7;
        Theme.sectionLabel(ctx, font, "About", x, y, Theme.TEXT_DIM);
        y += 15;

        // "Why is this installed?" — reverse-dependency lookup.
        List<String> dependents = graph == null ? List.of()
                : graph.getDependentsOf(meta.getId());
        if (!dependents.isEmpty()) {
            String names = dependents.stream()
                    .map(d -> FabricLoader.getInstance().getModContainer(d)
                            .map(c -> c.getMetadata().getName()).orElse(d))
                    .collect(Collectors.joining(", "));
                ctx.text(font,
                    Component.literal(Theme.clip(font, "Required by: " + names, rightW)),
                    x, y, Theme.BLUE, true);
                y += font.lineHeight + 4;
        }

        String desc = ModrinthDescriptionCache.getDescription(meta.getId(), meta.getDescription());
        if (desc == null || desc.isBlank()) desc = "No description available.";

        List<FormattedCharSequence> lines = font.split(Component.literal(desc), rightW);
        int lh = font.lineHeight + 2;
        int maxScroll = Math.max(0, lines.size() * lh - (bottom - y));
        detailScroll = Math.max(0, Math.min(detailScroll, maxScroll));

        ctx.enableScissor(x, y, x + rightW, bottom);
        int ly = y - detailScroll;
        for (FormattedCharSequence line : lines) {
            if (ly + lh >= y && ly <= bottom)
                ctx.text(font, line, x, ly, Theme.TEXT_MUTED, true);
            ly += lh;
        }
        ctx.disableScissor();
    }

    // ── Actions ──────────────────────────────────────────────────────────────
    private boolean hasConfigScreen(ModContainer mod) {
        if (mod == null) return false;
        try {
            Class<?> mm = Class.forName("com.terraformersmc.modmenu.ModMenu");
            return mm.getMethod("getConfigScreenFactory", String.class)
                    .invoke(null, mod.getMetadata().getId()) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void openConfig() {
        if (selected == null) return;
        String modId = selected.getMetadata().getId();
        try {
            Class<?> mm = Class.forName("com.terraformersmc.modmenu.ModMenu");
            Object factory = mm.getMethod("getConfigScreenFactory", String.class).invoke(null, modId);
            if (factory != null) {
                Screen cs = (Screen) factory.getClass().getMethod("create", Screen.class).invoke(factory, this);
                if (cs != null) { minecraft.gui.setScreen(cs); return; }
            }
        } catch (Exception e) {
            SuperModMenuClient.LOGGER.error("Failed to open config for {}", modId, e);
        }
        minecraft.gui.setScreen(new NoConfigScreen(this, selected.getMetadata().getName()));
    }

    private void toggleFavorite() {
        if (selected == null) return;
        ModDataManager.toggleFavorite(selected.getMetadata().getId());
        updateActionButtons();
        refreshFilter();
    }

    private void openNote() {
        if (selected == null) return;
        minecraft.gui.setScreen(new NoteEditorScreen(this,
                selected.getMetadata().getId(), selected.getMetadata().getName()));
    }

    private void openDeps() {
        if (selected == null) return;
        minecraft.gui.setScreen(new DependencyGraphScreen(this,
                selected.getMetadata().getId(),
                com.supermodmenu.dependency.DependencyGraph.build()));
    }

    private void installUpdate() {
        if (selected == null) return;
        // Open the mod's Modrinth page so the user can download the update manually.
        // Auto-download was removed because CurseForge (and Modrinth) don't allow
        // mods that spawn external processes to swap jar files.
        openModrinth();
    }

    private void openModrinth() {
        if (selected == null) return;
        var result = ModrinthUpdateChecker.getResult(selected.getMetadata().getId());
        String slug = ModrinthDescriptionCache.getSlug(selected.getMetadata().getId());
        String url = result.url() != null ? result.url()
                : slug != null ? "https://modrinth.com/mod/" + slug
                : "https://modrinth.com/mod/" + selected.getMetadata().getId();
        try {
            net.minecraft.util.Util.getPlatform().openUri(java.net.URI.create(url));
        } catch (Exception e) {
            SuperModMenuClient.LOGGER.error("Failed to open URL {}", url, e);
        }
    }

    private void promptRestart() {
        minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                confirmed -> {
                    if (confirmed) minecraft.stop();   // clean shutdown; relaunch applies new mods
                    else minecraft.gui.setScreen(this);
                },
                Component.literal("Restart Minecraft?").withStyle(ChatFormatting.BOLD),
                Component.literal("Mods were installed or updated. Quit now so they load on next launch?"),
                Component.literal("Quit Now"),
                Component.literal("Later")));
    }

    private void openModsFolder() {
        try {
            java.nio.file.Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
            java.nio.file.Files.createDirectories(modsDir);
            net.minecraft.util.Util.getPlatform().openUri(modsDir.toUri());
        } catch (Exception e) {
            SuperModMenuClient.LOGGER.error("Failed to open mods folder", e);
        }
    }

    private void checkUpdates() {
        if (ModrinthUpdateChecker.isChecking()) return;
        updatesBtn.setMessage(Component.literal("Checking…"));
        updatesBtn.active = false;
        String mc = SharedConstants.getCurrentVersion().name();
        ModrinthUpdateChecker.checkAllAsync(mc).thenRun(() -> minecraft.execute(() -> {
            updatesBtn.setMessage(Component.literal("⟳ Updates"));
            updatesBtn.active = true;
            refreshFilter();
        }));
    }

    // ── Input ──────────────────────────────────────────────────────────────--
    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (selected != null && mx >= rightX && mx <= rightX + rightW
                && my >= headerH && my <= actionTop) {
            detailScroll -= (int) (vAmt * 12);
            return true;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }
}
