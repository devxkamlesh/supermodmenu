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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

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

    // Translucent surfaces so the menu blur shows through (this screen only).
    private static final int PANEL_T = 0xC0141420;
    private static final int SCRIM   = 0x40000000;

    private final Screen parent;

    private TextFieldWidget searchBox;
    private ModListWidget   list;

    private ButtonWidget favBtn, noteBtn, depsBtn, configBtn, modrinthBtn, updateBtn;
    private ButtonWidget getModsBtn, updatesBtn, updatesBadge, restartBtn;
    private ButtonWidget linkWebBtn, linkSrcBtn, linkIssuesBtn;
    private String curHome, curSrc, curIssues;
    private int linkRowY;
    private final List<ButtonWidget> filterTabs = new ArrayList<>();

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
        super(Text.literal("Super Mod Menu"));
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
        headerH  = 44;
        footerH  = 34;
        listTop  = headerH + 52;

        rightX = sidebarW + Theme.PAD;
        rightW = width - rightX - Theme.PAD;

        int gap = 5;
        int gridH = 3 * Theme.BTN_H + 2 * gap;
        actionTop = height - footerH - gridH - Theme.PAD;

        // Search
        searchBox = new TextFieldWidget(textRenderer,
                Theme.PAD, headerH + 8, sidebarW - 2 * Theme.PAD, 18, Text.literal("Search"));
        searchBox.setPlaceholder(Text.literal("Search mods…").formatted(Formatting.DARK_GRAY));
        searchBox.setChangedListener(s -> refreshFilter());
        addDrawableChild(searchBox);

        // Filter segmented control
        int tabsY = headerH + 30, tabGap = 4;
        int tabW = (sidebarW - 2 * Theme.PAD - tabGap * (FilterMode.values().length - 1))
                / FilterMode.values().length;
        int tabX = Theme.PAD;
        filterTabs.clear();
        for (FilterMode mode : FilterMode.values()) {
            FilterMode m = mode;
            ButtonWidget tab = ButtonWidget.builder(Text.literal(mode.label),
                            b -> { filter = m; refreshFilter(); })
                    .dimensions(tabX, tabsY, tabW, 16).build();
            addDrawableChild(tab);
            filterTabs.add(tab);
            tabX += tabW + tabGap;
        }

        // Mod list
        int listH = (height - footerH) - listTop;
        list = new ModListWidget(client, sidebarW, listH, listTop, 36, this);
        addDrawableChild(list);

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
        updatesBadge = ButtonWidget.builder(Text.literal("⬆ updates"),
                        b -> { filter = FilterMode.HAS_UPDATE; refreshFilter(); })
                .dimensions(width - 130, 6, 120, 16).build();
        addDrawableChild(updatesBadge);

        // Header: "Restart to apply" — shown after a mod is installed/updated this session.
        restartBtn = ButtonWidget.builder(Text.literal("⟳ Restart to apply"), b -> promptRestart())
                .dimensions(width - 154, 24, 144, 16).build();
        restartBtn.visible = false;
        addDrawableChild(restartBtn);

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
                b -> client.setScreen(new ModrinthBrowserScreen(this)));
        fx += bw + Theme.GAP;
        updatesBtn = footer("⟳ Updates", fx, fy, bw, b -> checkUpdates());
        fx += bw + Theme.GAP;
        footer("\uD83D\uDCC1 Folder", fx, fy, bw, b -> openModsFolder());
        footer("Done", doneX, fy, doneW, b -> close());

        updateActionButtons();
        refreshFilter();
    }

    private ButtonWidget action(String label, int x, int y, int w, Runnable onClick) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> onClick.run())
                .dimensions(x, y, w, Theme.BTN_H).build();
        return addDrawableChild(b);
    }

    private ButtonWidget linkButton(String label, Runnable onClick) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> onClick.run())
                .dimensions(rightX, linkRowY, 60, 16).build();
        b.visible = false;
        return addDrawableChild(b);
    }

    private void openUrl(String url) {
        if (url == null || url.isBlank()) return;
        try {
            net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create(url));
        } catch (Exception e) {
            SuperModMenuClient.LOGGER.error("Failed to open URL {}", url, e);
        }
    }

    /** Position the visible Website/Source/Issues buttons in a left-to-right row. */
    private void layoutLinks() {
        int lx = rightX;
        ButtonWidget[] btns = {linkWebBtn, linkSrcBtn, linkIssuesBtn};
        String[] urls = {curHome, curSrc, curIssues};
        for (int i = 0; i < btns.length; i++) {
            ButtonWidget b = btns[i];
            if (b == null) continue;
            boolean show = selected != null && urls[i] != null && !urls[i].isBlank();
            if (show) {
                int w = textRenderer.getWidth(b.getMessage().getString()) + 14;
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

    private ButtonWidget footer(String label, int x, int y, int w, ButtonWidget.PressAction onClick) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), onClick)
                .dimensions(x, y, w, 20).build());
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
        String q = searchBox == null ? "" : searchBox.getText().toLowerCase(Locale.ROOT).trim();

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
        ButtonWidget[] all = {favBtn, noteBtn, depsBtn, configBtn, modrinthBtn, updateBtn};
        if (!has) {
            for (ButtonWidget b : all) if (b != null) b.visible = false;
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

        favBtn.setMessage(Text.literal(
                ModDataManager.isFavorite(id) ? "★ Unfavorite" : "☆ Favorite"));
        if (hasUpdate) refreshUpdateButtonLabel();

        // Build the ordered list of buttons that are actually shown, then flow them
        // into a 2-column grid (so the common case is a tidy 2×2).
        List<ButtonWidget> vis = new ArrayList<>();
        vis.add(favBtn);
        vis.add(noteBtn);
        if (hasUpdate) vis.add(updateBtn);
        vis.add(depsBtn);
        if (hasConfig) vis.add(configBtn);
        vis.add(modrinthBtn);

        for (ButtonWidget b : all) if (b != null) b.visible = false;
        for (int i = 0; i < vis.size(); i++) {
            ButtonWidget b = vis.get(i);
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
        updateBtn.setMessage(Text.literal("⬆ Update on Modrinth"));
        updateBtn.active = true;
    }

    // ── Render ─────────────────────────────────────────────────────────────--
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Vanilla menu blur (only on this screen). Panels below are translucent so
        // the blurred world shows through, like Mod Menu. Get Mods stays opaque.
        this.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(0, 0, width, height, SCRIM);
        ctx.fill(0, headerH, sidebarW, height - footerH, PANEL_T);
        ctx.fill(0, height - footerH, width, height, PANEL_T);
        Theme.panel(ctx, rightX - 4, headerH + 4, rightW + 8, (actionTop - 8) - (headerH + 4),
                PANEL_T, Theme.BORDER);

        // Keep the live update button label in sync while a download runs.
        if (updateBtn != null && updateBtn.visible) refreshUpdateButtonLabel();

        // Update badge (top-right): visible only when updates exist.
        int updateCount = (int) allMods.stream().filter(m ->
                ModrinthUpdateChecker.getResult(m.getMetadata().getId()).status()
                        == ModrinthUpdateChecker.Status.UPDATE_AVAILABLE).count();
        if (updatesBadge != null) {
            updatesBadge.visible = updateCount > 0;
            updatesBadge.setMessage(Text.literal("⬆ " + updateCount + " update"
                    + (updateCount == 1 ? "" : "s")));
        }
        if (restartBtn != null) {
            restartBtn.visible = ModrinthBrowserScreen.newModsInstalled;
        }

        drawHeader(ctx);

        if (restartBtn != null && restartBtn.visible)
            ctx.fill(restartBtn.getX(), restartBtn.getY(),
                    restartBtn.getX() + restartBtn.getWidth(),
                    restartBtn.getY() + restartBtn.getHeight(), Theme.GREEN_DIM);

        if (getModsBtn != null)
            ctx.fill(getModsBtn.getX(), getModsBtn.getY(),
                    getModsBtn.getX() + getModsBtn.getWidth(),
                    getModsBtn.getY() + getModsBtn.getHeight(), Theme.GREEN_DIM);
        if (updatesBtn != null)
            ctx.fill(updatesBtn.getX(), updatesBtn.getY(),
                    updatesBtn.getX() + updatesBtn.getWidth(),
                    updatesBtn.getY() + updatesBtn.getHeight(), Theme.BLUE_DIM);

        super.render(ctx, mouseX, mouseY, delta);

        // Active filter tab underline
        FilterMode[] vals = FilterMode.values();
        for (int i = 0; i < filterTabs.size(); i++) {
            if (vals[i] != filter) continue;
            ButtonWidget t = filterTabs.get(i);
            ctx.fill(t.getX(), t.getY() + t.getHeight(),
                    t.getX() + t.getWidth(), t.getY() + t.getHeight() + 2, Theme.ACCENT);
        }

        drawDetail(ctx);

        Theme.divider(ctx, 0, height - footerH, width);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(com.supermodmenu.Attribution.credit()),
                width / 2, height - footerH + 13, Theme.TEXT_DIM);
        if (ModrinthUpdateChecker.isChecking()) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("⟳ " + ModrinthUpdateChecker.getStatusMessage())
                            .formatted(Formatting.YELLOW),
                    rightX, height - footerH - 12, Theme.TEXT);
        }
    }

    private void drawHeader(DrawContext ctx) {
        ctx.fill(0, 0, width, headerH, PANEL_T);
        Theme.divider(ctx, 0, headerH, width);

        ctx.drawTextWithShadow(textRenderer,
                Text.literal("✦ Super Mod Menu").formatted(Formatting.BOLD),
                Theme.PAD, 9, Theme.ACCENT);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(allMods.size() + " mods · " + filteredMods.size() + " shown"),
                Theme.PAD, 24, Theme.TEXT_DIM);
    }

    private void drawDetail(DrawContext ctx) {
        int x = rightX;
        int top = headerH + Theme.PAD + 4;
        int bottom = actionTop - Theme.PAD;

        if (selected == null) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Select a mod to view details").formatted(Formatting.ITALIC),
                    rightX + rightW / 2, top + 30, Theme.TEXT_DIM);
            return;
        }

        var meta = selected.getMetadata();
        int y = top;

        int iconSz = 46;
        Identifier icon = ModIconCache.getIcon(meta.getId());
        if (icon != null) {
            ctx.drawTexture(RenderLayer::getGuiTextured, icon, x, y, 0, 0,
                    iconSz, iconSz, iconSz, iconSz);
        } else {
            Theme.panel(ctx, x, y, iconSz, iconSz, Theme.BG_ELEVATED, Theme.BORDER_LIGHT);
            String fl = meta.getName().isEmpty() ? "?" : meta.getName().substring(0, 1).toUpperCase();
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(fl).formatted(Formatting.BOLD),
                    x + iconSz / 2, y + (iconSz - textRenderer.fontHeight) / 2, Theme.TEXT_DIM);
        }

        int tx = x + iconSz + 10, tw = rightW - iconSz - 10;
        ModEnvironment env = meta.getEnvironment();
        String envLabel = env == ModEnvironment.CLIENT ? "Client"
                : env == ModEnvironment.SERVER ? "Server" : null;
        int envColor = env == ModEnvironment.SERVER ? 0xFF8A6D1F : 0xFF2F5BBF;

        // Name (bold, full width). The badge goes on the version line below so it can't
        // overlap the (wider-than-measured) bold name.
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(Theme.clip(textRenderer, meta.getName(), tw)).formatted(Formatting.BOLD),
                tx, y + 2, Theme.TEXT);

        // Version + environment badge on the same line
        String verText = "v" + Theme.shortVersion(meta.getVersion().getFriendlyString());
        ctx.drawTextWithShadow(textRenderer, Text.literal(verText), tx, y + 15, Theme.TEXT_MUTED);
        if (envLabel != null) {
            Theme.envBadge(ctx, textRenderer, envLabel,
                    tx + textRenderer.getWidth(verText) + 6, y + 13, envColor);
        }
        String authors = meta.getAuthors().stream().map(p -> p.getName()).collect(Collectors.joining(", "));
        if (!authors.isBlank()) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(Theme.clip(textRenderer, "By " + authors, tw)), tx, y + 26, Theme.TEXT_DIM);
        }

        y += iconSz + 8;

        // Reserve the link-button row (Website / Source / Issues) drawn as widgets.
        boolean hasLinks = (curHome != null && !curHome.isBlank())
                || (curSrc != null && !curSrc.isBlank())
                || (curIssues != null && !curIssues.isBlank());
        if (hasLinks) y = linkRowY + 20;

        var upd = ModrinthUpdateChecker.getResult(meta.getId());
        if (upd.status() == ModrinthUpdateChecker.Status.UPDATE_AVAILABLE) {
            Theme.banner(ctx, textRenderer, "⬆ Update available: " + upd.latestVersion(),
                    x, y, rightW, Theme.GREEN, Theme.GREEN_DIM);
            y += 22;
        }
        String note = ModDataManager.getNote(meta.getId());
        if (!note.isBlank()) {
            Theme.banner(ctx, textRenderer, Theme.clip(textRenderer, "✎ " + note, rightW - 12),
                    x, y, rightW, Theme.ACCENT, Theme.ACCENT_DIM);
            y += 22;
        }

        Theme.divider(ctx, x, y, rightW);
        y += 6;

        // "Why is this installed?" — reverse-dependency lookup.
        List<String> dependents = graph == null ? List.of()
                : graph.getDependentsOf(meta.getId());
        if (!dependents.isEmpty()) {
            String names = dependents.stream()
                    .map(d -> FabricLoader.getInstance().getModContainer(d)
                            .map(c -> c.getMetadata().getName()).orElse(d))
                    .collect(Collectors.joining(", "));
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(Theme.clip(textRenderer, "Required by: " + names, rightW)),
                    x, y, Theme.BLUE);
            y += textRenderer.fontHeight + 4;
        }

        String desc = ModrinthDescriptionCache.getDescription(meta.getId(), meta.getDescription());
        if (desc == null || desc.isBlank()) desc = "No description available.";

        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(desc), rightW);
        int lh = textRenderer.fontHeight + 2;
        int maxScroll = Math.max(0, lines.size() * lh - (bottom - y));
        detailScroll = Math.max(0, Math.min(detailScroll, maxScroll));

        ctx.enableScissor(x, y, x + rightW, bottom);
        int ly = y - detailScroll;
        for (OrderedText line : lines) {
            if (ly + lh >= y && ly <= bottom)
                ctx.drawTextWithShadow(textRenderer, line, x, ly, Theme.TEXT_MUTED);
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
                if (cs != null) { client.setScreen(cs); return; }
            }
        } catch (Exception e) {
            SuperModMenuClient.LOGGER.error("Failed to open config for {}", modId, e);
        }
        client.setScreen(new NoConfigScreen(this, selected.getMetadata().getName()));
    }

    private void toggleFavorite() {
        if (selected == null) return;
        ModDataManager.toggleFavorite(selected.getMetadata().getId());
        updateActionButtons();
        refreshFilter();
    }

    private void openNote() {
        if (selected == null) return;
        client.setScreen(new NoteEditorScreen(this,
                selected.getMetadata().getId(), selected.getMetadata().getName()));
    }

    private void openDeps() {
        if (selected == null) return;
        client.setScreen(new DependencyGraphScreen(this,
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
            net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create(url));
        } catch (Exception e) {
            SuperModMenuClient.LOGGER.error("Failed to open URL {}", url, e);
        }
    }

    private void promptRestart() {
        client.setScreen(new net.minecraft.client.gui.screen.ConfirmScreen(
                confirmed -> {
                    if (confirmed) client.scheduleStop();   // clean shutdown; relaunch applies new mods
                    else client.setScreen(this);
                },
                Text.literal("Restart Minecraft?").formatted(Formatting.BOLD),
                Text.literal("Mods were installed or updated. Quit now so they load on next launch?"),
                Text.literal("Quit Now"),
                Text.literal("Later")));
    }

    private void openModsFolder() {
        try {
            java.nio.file.Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
            java.nio.file.Files.createDirectories(modsDir);
            net.minecraft.util.Util.getOperatingSystem().open(modsDir.toUri());
        } catch (Exception e) {
            SuperModMenuClient.LOGGER.error("Failed to open mods folder", e);
        }
    }

    private void checkUpdates() {
        if (ModrinthUpdateChecker.isChecking()) return;
        updatesBtn.setMessage(Text.literal("Checking…"));
        updatesBtn.active = false;
        String mc = SharedConstants.getGameVersion().getName();
        ModrinthUpdateChecker.checkAllAsync(mc).thenRun(() -> client.execute(() -> {
            updatesBtn.setMessage(Text.literal("⟳ Updates"));
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() { client.setScreen(parent); }
}
