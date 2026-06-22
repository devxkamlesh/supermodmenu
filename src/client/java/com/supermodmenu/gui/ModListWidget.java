package com.supermodmenu.gui;

import com.supermodmenu.data.ModDataManager;
import com.supermodmenu.icon.ModIconCache;
import com.supermodmenu.update.ModrinthUpdateChecker;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.fabricmc.loader.api.metadata.ModEnvironment;

import java.util.List;

/**
 * Scrollable list of mods rendered as compact cards.
 *
 * Visual contract is delegated to {@link Theme}; this widget only owns layout
 * and interaction (selection, right-click to edit a note).
 */
public class ModListWidget extends AlwaysSelectedEntryListWidget<ModListWidget.ModEntry> {

    private final ModListScreen parent;

    public ModListWidget(MinecraftClient client, int width, int height,
                         int top, int itemHeight, ModListScreen parent) {
        super(client, width, height, top, itemHeight);
        this.parent = parent;
    }

    public void setMods(List<ModContainer> mods) {
        clearEntries();
        for (ModContainer mod : mods) addEntry(new ModEntry(mod));
    }

    @Override
    public int getRowWidth() {
        return getWidth() - 12;
    }

    @Override
    protected int getScrollbarX() {
        return getRight() - 6;
    }

    // ── Entry ───────────────────────────────────────────────────────────────--

    public class ModEntry extends AlwaysSelectedEntryListWidget.Entry<ModEntry> {

        private final ModContainer mod;

        ModEntry(ModContainer mod) { this.mod = mod; }

        @Override
        public Text getNarration() {
            return Text.literal(mod.getMetadata().getName());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            parent.selectMod(mod);
            ModListWidget.this.setSelected(this);
            if (button == 1) {
                client.setScreen(new NoteEditorScreen(parent,
                        mod.getMetadata().getId(), mod.getMetadata().getName()));
            }
            return true;
        }

        @Override
        public void render(DrawContext ctx, int index, int y, int x,
                           int entryWidth, int entryHeight, int mouseX, int mouseY,
                           boolean hovered, float tickDelta) {

            String id = mod.getMetadata().getId();
            boolean fav       = ModDataManager.isFavorite(id);
            boolean selected  = this == ModListWidget.this.getSelectedOrNull();
            boolean hasUpdate = ModrinthUpdateChecker.getResult(id).status()
                    == ModrinthUpdateChecker.Status.UPDATE_AVAILABLE;

            // Card geometry (small inset gives breathing room between rows)
            int cx = x + 2, cy = y + 2;
            int cw = entryWidth - 4, ch = entryHeight - 4;

            int bg = selected ? 0xFF302841 : hovered ? Theme.BG_CARD_HOV : Theme.BG_CARD;
            ctx.fill(cx, cy, cx + cw, cy + ch, bg);
            ctx.drawBorder(cx, cy, cw, ch, Theme.BORDER);   // no orange; selection box marks selected

            // Icon
            int iconSize = ch - 8;
            int iconX = cx + 6, iconY = cy + 4;
            Identifier icon = ModIconCache.getIcon(id);
            if (icon != null) {
                ctx.drawTexture(RenderLayer::getGuiTextured, icon,
                        iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
            } else {
                ctx.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, Theme.BG_ELEVATED);
                ctx.drawBorder(iconX, iconY, iconSize, iconSize, Theme.BORDER_LIGHT);
                String letter = mod.getMetadata().getName().isEmpty() ? "?"
                        : mod.getMetadata().getName().substring(0, 1).toUpperCase();
                ctx.drawCenteredTextWithShadow(client.textRenderer,
                        Text.literal(letter).formatted(Formatting.BOLD),
                        iconX + iconSize / 2,
                        iconY + (iconSize - client.textRenderer.fontHeight) / 2,
                        Theme.TEXT_DIM);
            }

            int textX = iconX + iconSize + 8;
            int rightPad = (hasUpdate ? 16 : 0) + (fav ? 12 : 0) + 8;
            int textW = cx + cw - textX - rightPad;

            // Name + environment badge (Mod Menu style)
            ModEnvironment env = mod.getMetadata().getEnvironment();
            String envLabel = env == ModEnvironment.CLIENT ? "Client"
                    : env == ModEnvironment.SERVER ? "Server" : null;
            int envColor = env == ModEnvironment.SERVER ? 0xFF8A6D1F : 0xFF2F5BBF;
            int badgeReserve = envLabel != null
                    ? client.textRenderer.getWidth(envLabel) + 8 + 5 : 0;

            String name = Theme.clip(client.textRenderer, mod.getMetadata().getName(), textW - badgeReserve);
            int nameW = client.textRenderer.getWidth(name);
            ctx.drawTextWithShadow(client.textRenderer, Text.literal(name),
                    textX, cy + 5, Theme.TEXT);
            if (envLabel != null) {
                Theme.envBadge(ctx, client.textRenderer, envLabel, textX + nameW + 5, cy + 4, envColor);
            }

            // Version + id (second line, muted)
            String ver = Theme.shortVersion(mod.getMetadata().getVersion().getFriendlyString());
            String sub = Theme.clip(client.textRenderer, "v" + ver + "  ·  " + id, textW);
            ctx.drawTextWithShadow(client.textRenderer, Text.literal(sub),
                    textX, cy + 5 + client.textRenderer.fontHeight + 2, Theme.TEXT_DIM);

            // Right-side markers
            int markX = cx + cw - 8;
            if (hasUpdate) {
                markX -= 10;
                ctx.drawTextWithShadow(client.textRenderer,
                        Text.literal("⬆").formatted(Formatting.GREEN), markX, cy + 6, Theme.TEXT);
            }
            if (fav) {
                markX -= 12;
                ctx.drawTextWithShadow(client.textRenderer,
                        Text.literal("★"), markX, cy + 6, Theme.GOLD);
            }
        }
    }
}
