package dev.ringworld.client.compat;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

/**
 * Optional renderer adapters do not load foreign classes during discovery.
 */
public final class RingOptionalRenderMixins implements IMixinConfigPlugin {
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.contains(".compat.")) return true;
        var loader = getClass().getClassLoader();
        return loader.getResource("net/caffeinemc/mods/sodium/client/SodiumClientMod.class") != null && loader.getResource(targetClassName.replace('.', '/') + ".class") != null;
    }
}
