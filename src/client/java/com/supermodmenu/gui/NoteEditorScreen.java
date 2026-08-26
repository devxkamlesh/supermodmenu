package com.supermodmenu.gui;

import com.supermodmenu.data.ModDataManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/**
 * Simple screen for editing a personal note attached to a mod.
 */
public class NoteEditorScreen extends Screen {

    private final Screen parent;
    private final String modId;
    private final String modName;
    private EditBox noteField;

    public NoteEditorScreen(Screen parent, String modId, String modName) {
        super(Component.literal("Note: " + modName));
        this.parent  = parent;
        this.modId   = modId;
        this.modName = modName;
    }

    @Override
    protected void init() {
        int w = Math.min(width - 40, 360);
        int h = 20;
        int x = (width - w) / 2;
        int y = height / 2 - 20;

        noteField = new EditBox(font, x, y, w, h,
            Component.literal("Your note..."));
        noteField.setMaxLength(256);
        noteField.setValue(ModDataManager.getNote(modId));
        noteField.setFocused(true);
        addRenderableWidget(noteField);

        // Save
        addRenderableWidget(Button.builder(Component.literal("Save"), btn -> {
            ModDataManager.setNote(modId, noteField.getValue());
            minecraft.gui.setScreen(parent);
        }).bounds(x, y + 28, w / 2 - 2, 20).build());

        // Cancel
        addRenderableWidget(Button.builder(Component.literal("Cancel"),
            btn -> minecraft.gui.setScreen(parent))
            .bounds(x + w / 2 + 2, y + 28, w / 2 - 2, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, Theme.BG_APP);
        int panelW = Math.min(width - 24, 392);
        int panelX = (width - panelW) / 2;
        Theme.accentedPanel(context, panelX, height / 2 - 62, panelW, 124, Theme.GOLD);
        context.centeredText(font,
            Component.literal("PERSONAL NOTE").withStyle(ChatFormatting.BOLD),
                width / 2, height / 2 - 47, Theme.GOLD);
        context.centeredText(font, Component.literal(modName),
                width / 2, height / 2 - 33, Theme.TEXT_MUTED);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 257 || event.key() == 335) { // Enter / numpad Enter
            ModDataManager.setNote(modId, noteField.getValue());
            minecraft.gui.setScreen(parent);
            return true;
        }
        if (event.key() == 256) { // ESC
            minecraft.gui.setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }
}
