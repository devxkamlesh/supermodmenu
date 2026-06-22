package com.supermodmenu.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Shown when a mod has no registered configuration screen.
 * Replaces the broken ConfirmScreen (two buttons, one blank).
 */
public class NoConfigScreen extends Screen {

    private static final int BG_PANEL  = 0xF01A1A24;
    private static final int BORDER    = 0xFF2E2E40;
    private static final int COLOR_DIM = 0x88000000;

    private final Screen parent;
    private final String modName;

    public NoConfigScreen(Screen parent, String modName) {
        super(Text.literal("No Configuration Available"));
        this.parent  = parent;
        this.modName = modName;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(
                Text.literal("OK"),
                btn -> client.setScreen(parent))
                .dimensions(width / 2 - 60, height / 2 + 20, 120, 24)
                .build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Dim the world/previous screen behind the dialog
        ctx.fill(0, 0, width, height, COLOR_DIM);

        int panelW = 320;
        int panelH = 110;
        int panelX = (width  - panelW) / 2;
        int panelY = (height - panelH) / 2;

        // Panel background + border
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG_PANEL);
        ctx.drawBorder(panelX, panelY, panelW, panelH, BORDER);

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("No Configuration Available").formatted(Formatting.YELLOW),
                width / 2, panelY + 18, 0xFFFFFFFF);

        // Body
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(modName + " does not have a configuration screen."),
                width / 2, panelY + 42, 0xFFAAAAAA);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            client.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
