package dev.ringworld.client.mixin.compat;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.shaders.ShaderType;
import dev.ringworld.client.compat.SodiumTerrainShaders;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Map;

@Mixin(ShaderManager.class)
public abstract class SodiumEngineShaderMixin {
    @Redirect(method = "loadShader", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/Resource;openAsReader()Ljava/io/BufferedReader;"))
    private static BufferedReader ringworld$material(Resource resource, Identifier name, Resource original, ShaderType type, Map<Identifier, Resource> resources, ImmutableMap.Builder<?, ?> builder) throws java.io.IOException {
        try (var reader = resource.openAsReader()) {
            return new BufferedReader(new StringReader(SodiumTerrainShaders.adapt(name, IOUtils.toString(reader))));
        }
    }
}
