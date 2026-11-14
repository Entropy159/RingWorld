package dev.ringworld.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ringworld.server.RingWorldServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BiomeSource.class)
public abstract class BiomeSourceMixin {
    @ModifyVariable(method = "findClosestBiome3d", at = @At("STORE"), name = "blockX")
    private int wrap3DX(int blockX, @Local(argsOnly = true, name = "level") LevelReader level) {
        if (level instanceof ServerLevel serverLevel) {
            var geometry = RingWorldServer.geometryFor(serverLevel);
            return geometry == null ? blockX : geometry.wrapBlockX(blockX);
        }
        return blockX;
    }
}
