package com.supermodmenu.mixin;

import com.supermodmenu.gui.ModListScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects a "Mods" button into the in-game pause menu.
 * Uses the Screen#init method which is always present.
 */
@Mixin(PauseScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

    protected GameMenuScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void supermodmenu$addModsButton(CallbackInfo ci) {
        int modCount = (int) FabricLoader.getInstance().getAllMods().stream()
                .filter(m -> !m.getMetadata().getType().equals("builtin"))
                .filter(m -> m.getContainingMod().isEmpty())
                .filter(m -> {
                    try { return m.getOrigin().getKind()
                            != net.fabricmc.loader.api.metadata.ModOrigin.Kind.NESTED; }
                    catch (Exception e) { return true; }
                })
                .count();

        int btnW = 200;
        int btnH = 20;
        int btnX = this.width / 2 - btnW / 2;
        int btnY = this.height / 4 + 168;

        this.addRenderableWidget(Button.builder(
            Component.literal("✦ Mods (" + modCount + ")"),
            btn -> this.minecraft.gui.setScreen(new ModListScreen(this)))
            .bounds(btnX, btnY, btnW, btnH)
                .build());
    }
}
