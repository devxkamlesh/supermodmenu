package com.supermodmenu.mixin;

import com.supermodmenu.gui.ModListScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects a "Mods (N)" button into the main title screen.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends net.minecraft.client.gui.screen.Screen {

    protected TitleScreenMixin() {
        super(Text.empty());
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

        int btnW = 100;
        int btnH = 20;
        int btnX = this.width - btnW - 8;
        int btnY = this.height - btnH - 8;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("✦ Mods (" + modCount + ")"),
                btn -> this.client.setScreen(new ModListScreen(this)))
                .dimensions(btnX, btnY, btnW, btnH)
                .build());
    }
}
