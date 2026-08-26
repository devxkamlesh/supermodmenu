package com.supermodmenu.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/**
 * Shown when a mod has no registered configuration screen.
 * Replaces the broken ConfirmScreen (two buttons, one blank).
 */
public class NoConfigScreen extends Screen {

    private final Screen parent;
    private final String modName;

    public NoConfigScreen(Screen parent, String modName) {
        super(Component.literal("No Configuration Available"));
        this.parent  = parent;
        this.modName = modName;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
            Component.literal("OK"),
            btn -> minecraft.gui.setScreen(parent))
            .bounds(width / 2 - 60, height / 2 + 20, 120, 24)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Dim the world/previous screen behind the dialog
        ctx.fill(0, 0, width, height, Theme.BG_SCRIM);

        int panelW = 320;
        int panelH = 110;
        int panelX = (width  - panelW) / 2;
        int panelY = (height - panelH) / 2;

        // Panel background + border
        Theme.accentedPanel(ctx, panelX, panelY, panelW, panelH, Theme.BLUE);

        // Title
        ctx.centeredText(font,
            Component.literal("NO CONFIGURATION AVAILABLE").withStyle(ChatFormatting.BOLD),
                width / 2, panelY + 18, Theme.BLUE);

        // Body
        ctx.centeredText(font,
            Component.literal(modName + " does not have a configuration screen."),
                width / 2, panelY + 42, Theme.TEXT_MUTED);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { // ESC
            minecraft.gui.setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
