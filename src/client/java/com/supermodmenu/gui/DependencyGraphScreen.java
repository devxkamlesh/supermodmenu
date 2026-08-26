package com.supermodmenu.gui;

import com.supermodmenu.dependency.DependencyGraph;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * Displays the dependency tree for a selected mod.
 *
 * Shows:
 *  - What this mod depends on (tree going down)
 *  - What mods depend on this mod (reverse deps)
 */
public class DependencyGraphScreen extends Screen {

    private final Screen parent;
    private final String rootModId;
    private final DependencyGraph graph;

    private int scrollOffset = 0;
    private static final int LINE_H = 12;

    public DependencyGraphScreen(Screen parent, String rootModId, DependencyGraph graph) {
        super(Component.literal("Dependencies: " + rootModId));
        this.parent    = parent;
        this.rootModId = rootModId;
        this.graph     = graph;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("← Back"),
            btn -> minecraft.gui.setScreen(parent))
            .bounds(8, height - 28, 80, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, Theme.BG_APP);
        context.fill(0, 0, width, 26, Theme.BG_HEADER);
        context.fill(0, 0, 4, 26, Theme.BLUE);
        Theme.divider(context, 0, 26, width);
        Theme.panel(context, 10, 32, width - 20, height - 68, Theme.BG_PANEL, Theme.BORDER);
        super.extractRenderState(context, mouseX, mouseY, delta);

        String modName = FabricLoader.getInstance()
                .getModContainer(rootModId)
                .map(m -> m.getMetadata().getName())
                .orElse(rootModId);

        context.centeredText(font,
            Component.literal("Dependency Tree: ").append(
                Component.literal(modName).withStyle(ChatFormatting.AQUA)),
                width / 2, 9, Theme.TEXT);

            int x = 22;
            int y = 42 - scrollOffset;

        // ── What this mod depends on ──────────────────────────────────────────
        context.text(font,
            Component.literal("▼ DEPENDS ON").withStyle(ChatFormatting.AQUA), x, y, Theme.TEXT, true);
        y += LINE_H + 2;

        List<DependencyGraph.Edge> deps = graph.getDependenciesOf(rootModId);
        if (deps.isEmpty()) {
                context.text(font,
                    Component.literal("  (none)").withStyle(ChatFormatting.DARK_GRAY), x, y, 0xFFFFFF, true);
            y += LINE_H;
        } else {
            for (DependencyGraph.Edge edge : deps) {
                String label = "  • " + edge.to();
                if (edge.kind() == net.fabricmc.loader.api.metadata.ModDependency.Kind.RECOMMENDS) {
                    label += " (recommended)";
                }
                boolean installed = FabricLoader.getInstance().isModLoaded(edge.to());
                ChatFormatting color = installed ? ChatFormatting.GREEN : ChatFormatting.RED;
                context.text(font,
                    Component.literal(label).withStyle(color), x, y, 0xFFFFFF, true);
                y += LINE_H;
            }
        }

        y += 8;

        // ── What depends on this mod ──────────────────────────────────────────
        context.text(font,
            Component.literal("▲ REQUIRED BY").withStyle(ChatFormatting.AQUA), x, y, Theme.TEXT, true);
        y += LINE_H + 2;

        List<String> dependents = graph.getDependentsOf(rootModId);
        if (dependents.isEmpty()) {
                context.text(font,
                    Component.literal("  (nothing depends on this mod)").withStyle(ChatFormatting.DARK_GRAY),
                    x, y, 0xFFFFFF, true);
        } else {
            for (String dep : dependents) {
                String depName = FabricLoader.getInstance()
                        .getModContainer(dep)
                        .map(m -> m.getMetadata().getName())
                        .orElse(dep);
                context.text(font,
                    Component.literal("  • " + depName).withStyle(ChatFormatting.LIGHT_PURPLE),
                    x, y, 0xFFFFFF, true);
                y += LINE_H;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset = Math.max(0, scrollOffset - (int)(verticalAmount * LINE_H * 3));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { minecraft.gui.setScreen(parent); return true; }
        return super.keyPressed(event);
    }
}
