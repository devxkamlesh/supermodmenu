package com.supermodmenu.gui;

import com.supermodmenu.dependency.DependencyGraph;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
        super(Text.literal("Dependencies: " + rootModId));
        this.parent    = parent;
        this.rootModId = rootModId;
        this.graph     = graph;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("← Back"),
                btn -> client.setScreen(parent))
                .dimensions(8, height - 28, 80, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        String modName = FabricLoader.getInstance()
                .getModContainer(rootModId)
                .map(m -> m.getMetadata().getName())
                .orElse(rootModId);

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Dependency Tree: ").append(
                        Text.literal(modName).formatted(Formatting.AQUA)),
                width / 2, 8, 0xFFFFFF);

        int x = 16;
        int y = 28 - scrollOffset;

        // ── What this mod depends on ──────────────────────────────────────────
        context.drawTextWithShadow(textRenderer,
                Text.literal("▼ Depends on:").formatted(Formatting.YELLOW), x, y, 0xFFFFFF);
        y += LINE_H + 2;

        List<DependencyGraph.Edge> deps = graph.getDependenciesOf(rootModId);
        if (deps.isEmpty()) {
            context.drawTextWithShadow(textRenderer,
                    Text.literal("  (none)").formatted(Formatting.DARK_GRAY), x, y, 0xFFFFFF);
            y += LINE_H;
        } else {
            for (DependencyGraph.Edge edge : deps) {
                String label = "  • " + edge.to();
                if (edge.kind() == net.fabricmc.loader.api.metadata.ModDependency.Kind.RECOMMENDS) {
                    label += " (recommended)";
                }
                boolean installed = FabricLoader.getInstance().isModLoaded(edge.to());
                Formatting color = installed ? Formatting.GREEN : Formatting.RED;
                context.drawTextWithShadow(textRenderer,
                        Text.literal(label).formatted(color), x, y, 0xFFFFFF);
                y += LINE_H;
            }
        }

        y += 8;

        // ── What depends on this mod ──────────────────────────────────────────
        context.drawTextWithShadow(textRenderer,
                Text.literal("▲ Required by:").formatted(Formatting.YELLOW), x, y, 0xFFFFFF);
        y += LINE_H + 2;

        List<String> dependents = graph.getDependentsOf(rootModId);
        if (dependents.isEmpty()) {
            context.drawTextWithShadow(textRenderer,
                    Text.literal("  (nothing depends on this mod)").formatted(Formatting.DARK_GRAY),
                    x, y, 0xFFFFFF);
        } else {
            for (String dep : dependents) {
                String depName = FabricLoader.getInstance()
                        .getModContainer(dep)
                        .map(m -> m.getMetadata().getName())
                        .orElse(dep);
                context.drawTextWithShadow(textRenderer,
                        Text.literal("  • " + depName).formatted(Formatting.LIGHT_PURPLE),
                        x, y, 0xFFFFFF);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { client.setScreen(parent); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
