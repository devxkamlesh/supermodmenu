package com.supermodmenu.icon;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Creates a dynamic texture using the Minecraft 26.2 constructor.
 */
public final class TextureCompat {

    private TextureCompat() {}

    public static DynamicTexture create(NativeImage image) {
        return new DynamicTexture(() -> "Mod Menu Pro icon", image);
    }
}
