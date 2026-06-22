package com.supermodmenu.icon;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

import java.lang.reflect.Constructor;

/**
 * Cross-version construction of {@link NativeImageBackedTexture}.
 *
 * The constructor signature changed between Minecraft versions:
 *   - 1.21.4 and earlier:  {@code NativeImageBackedTexture(NativeImage)}
 *   - 1.21.5+:             {@code NativeImageBackedTexture(String/Supplier label, NativeImage)}
 *
 * Calling the old constructor directly throws {@link NoSuchMethodError} at runtime on
 * the newer game. We instead pick whatever constructor actually exists at runtime via
 * reflection, so a single jar works across these versions.
 */
public final class TextureCompat {

    private TextureCompat() {}

    public static NativeImageBackedTexture create(NativeImage image) {
        for (Constructor<?> c : NativeImageBackedTexture.class.getConstructors()) {
            Class<?>[] params = c.getParameterTypes();
            int imageIndex = -1;
            for (int i = 0; i < params.length; i++) {
                if (params[i] == NativeImage.class) { imageIndex = i; break; }
            }
            if (imageIndex < 0) continue;

            Object[] args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                args[i] = (i == imageIndex) ? image : defaultValue(params[i]);
            }
            try {
                return (NativeImageBackedTexture) c.newInstance(args);
            } catch (Throwable ignored) {
                // try the next matching constructor
            }
        }
        throw new IllegalStateException("No usable NativeImageBackedTexture constructor for this MC version");
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class)     return 0;
        if (type == long.class)    return 0L;
        if (type == float.class)   return 0f;
        if (type == double.class)  return 0d;
        return null; // String, Supplier<String>, etc.
    }
}
