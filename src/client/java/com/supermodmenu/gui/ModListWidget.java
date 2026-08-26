package com.supermodmenu.gui;

import com.supermodmenu.data.ModDataManager;
import com.supermodmenu.icon.ModIconCache;
import com.supermodmenu.update.ModrinthUpdateChecker;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.fabricmc.loader.api.metadata.ModEnvironment;

import java.util.List;

/**
 * Scrollable list of mods rendered as compact cards.
 *
 * Visual contract is delegated to {@link Theme}; this widget only owns layout
 * and interaction (selection, right-click to edit a note).
 */
public class ModListWidget extends ObjectSelectionList<ModListWidget.ModEntry> {

    private final ModListScreen parent;

    public ModListWidget(Minecraft client, int width, int height,
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
    protected int scrollBarX() {
        return getRight() - 6;
    }

    // ── Entry ───────────────────────────────────────────────────────────────--

    public class ModEntry extends ObjectSelectionList.Entry<ModEntry> {

        private final ModContainer mod;

        ModEntry(ModContainer mod) { this.mod = mod; }

        @Override
        public Component getNarration() {
            return Component.literal(mod.getMetadata().getName());
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            parent.selectMod(mod);
            ModListWidget.this.setSelected(this);
            if (event.button() == 1) {
                minecraft.gui.setScreen(new NoteEditorScreen(parent,
                        mod.getMetadata().getId(), mod.getMetadata().getName()));
            }
            return true;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                                   boolean hovered, float tickDelta) {
            int x = getContentX(), y = getContentY();
            int entryWidth = getContentWidth(), entryHeight = getContentHeight();

            String id = mod.getMetadata().getId();
            boolean fav       = ModDataManager.isFavorite(id);
            boolean selected  = this == ModListWidget.this.getSelected();
            boolean hasUpdate = ModrinthUpdateChecker.getResult(id).status()
                    == ModrinthUpdateChecker.Status.UPDATE_AVAILABLE;

            // Card geometry (small inset gives breathing room between rows)
            int cx = x + 2, cy = y + 2;
            int cw = entryWidth - 4, ch = entryHeight - 4;

                int bg = selected ? Theme.BG_SELECTED : hovered ? Theme.BG_CARD_HOV : Theme.BG_CARD;
            ctx.fill(cx, cy, cx + cw, cy + ch, bg);
                ctx.outline(cx, cy, cw, ch,
                    selected ? Theme.ACCENT : hovered ? Theme.BORDER_LIGHT : Theme.BORDER);
                if (selected) Theme.accentBar(ctx, cx, cy, ch, Theme.ACCENT);
                else if (hasUpdate) Theme.accentBar(ctx, cx, cy, ch, Theme.BLUE);

            // Icon
            int iconSize = ch - 8;
            int iconX = cx + 8, iconY = cy + 4;
            Identifier icon = ModIconCache.getIcon(id);
            if (icon != null) {
                ctx.blit(RenderPipelines.GUI_TEXTURED, icon,
                        iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
            } else {
                ctx.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, Theme.BG_ELEVATED);
                ctx.outline(iconX, iconY, iconSize, iconSize, Theme.BORDER_LIGHT);
                String letter = mod.getMetadata().getName().isEmpty() ? "?"
                        : mod.getMetadata().getName().substring(0, 1).toUpperCase();
                ctx.centeredText(minecraft.font,
                    Component.literal(letter).withStyle(ChatFormatting.BOLD),
                        iconX + iconSize / 2,
                        iconY + (iconSize - minecraft.font.lineHeight) / 2,
                        Theme.TEXT_DIM);
            }

            int textX = iconX + iconSize + 8;
            int rightPad = (hasUpdate ? 16 : 0) + (fav ? 12 : 0) + 8;
            int textW = cx + cw - textX - rightPad;

            // Name + environment badge (Mod Menu style)
            ModEnvironment env = mod.getMetadata().getEnvironment();
            String envLabel = env == ModEnvironment.CLIENT ? "Client"
                    : env == ModEnvironment.SERVER ? "Server" : null;
            int envColor = env == ModEnvironment.SERVER ? 0xFF6B541D : 0xFF244F78;
            int badgeReserve = envLabel != null
                    ? minecraft.font.width(envLabel) + 8 + 5 : 0;

                String name = Theme.clip(minecraft.font, mod.getMetadata().getName(), textW - badgeReserve);
                int nameW = minecraft.font.width(name);
                ctx.text(minecraft.font, Component.literal(name),
                    textX, cy + 5, Theme.TEXT, true);
            if (envLabel != null) {
                Theme.envBadge(ctx, minecraft.font, envLabel, textX + nameW + 5, cy + 4, envColor);
            }

            // Version + id (second line, muted)
            String ver = Theme.shortVersion(mod.getMetadata().getVersion().getFriendlyString());
                String sub = Theme.clip(minecraft.font, "v" + ver + "  ·  " + id, textW);
                ctx.text(minecraft.font, Component.literal(sub),
                    textX, cy + 5 + minecraft.font.lineHeight + 2, Theme.TEXT_DIM, true);

            // Right-side markers
            int markX = cx + cw - 8;
            if (hasUpdate) {
                markX -= 10;
                ctx.text(minecraft.font,
                    Component.literal("⬆"), markX, cy + 6, Theme.BLUE, true);
            }
            if (fav) {
                markX -= 12;
                ctx.text(minecraft.font,
                    Component.literal("★"), markX, cy + 6, Theme.GOLD, true);
            }
        }
    }
}
