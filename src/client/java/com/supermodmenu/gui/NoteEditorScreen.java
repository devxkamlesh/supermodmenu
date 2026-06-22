package com.supermodmenu.gui;

import com.supermodmenu.data.ModDataManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Simple screen for editing a personal note attached to a mod.
 */
public class NoteEditorScreen extends Screen {

    private final Screen parent;
    private final String modId;
    private final String modName;
    private TextFieldWidget noteField;

    public NoteEditorScreen(Screen parent, String modId, String modName) {
        super(Text.literal("Note: " + modName));
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

        noteField = new TextFieldWidget(textRenderer, x, y, w, h,
                Text.literal("Your note..."));
        noteField.setMaxLength(256);
        noteField.setText(ModDataManager.getNote(modId));
        noteField.setFocused(true);
        addDrawableChild(noteField);

        // Save
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), btn -> {
            ModDataManager.setNote(modId, noteField.getText());
            client.setScreen(parent);
        }).dimensions(x, y + 28, w / 2 - 2, 20).build());

        // Cancel
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"),
                btn -> client.setScreen(parent))
                .dimensions(x + w / 2 + 2, y + 28, w / 2 - 2, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("📝 Note for ").append(
                        Text.literal(modName).formatted(Formatting.YELLOW)),
                width / 2, height / 2 - 40, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / numpad Enter
            ModDataManager.setNote(modId, noteField.getText());
            client.setScreen(parent);
            return true;
        }
        if (keyCode == 256) { // ESC
            client.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
