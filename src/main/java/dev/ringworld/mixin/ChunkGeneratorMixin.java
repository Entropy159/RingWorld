package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingWorldGeneratorAccess;
import dev.ringworld.world.wall.RingGenerationBoundary;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps finite-band mutations in the asynchronous generation pipeline.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"), cancellable = true)
    private void ringworld$skipExteriorFeatures(WorldGenLevel world, ChunkAccess chunk, StructureManager structures, CallbackInfo ci) {
        if (!isExterior(chunk)) return;
        // An adjacent interior feature can spill a few blocks into this chunk
        // before its own feature stage. Clear those off-thread, then leave the
        // exterior empty without scheduling live chunk updates.
        RingGenerationBoundary.clearExterior(chunk);
        ci.cancel();
    }

    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void ringworld$buildRimAfterFeatures(WorldGenLevel world, ChunkAccess chunk, StructureManager structures, CallbackInfo ci) {
        RingWorldGeneratorAccess access = ringworld$access();
        if (access == null) return;
        RingGeometry geometry = access.ringworld$getGeometry();
        if (geometry != null) {
            RingGenerationBoundary.installRim(chunk, geometry, access.ringworld$getWallHeight(), access.ringworld$getWallStyle(), world.getSeed());
            RingGenerationBoundary.installUnderside(chunk, geometry, access.ringworld$getWallStyle(), world.getSeed());
        }
    }

    @Unique
    private RingWorldGeneratorAccess ringworld$access() {
        return (Object) this instanceof RingWorldGeneratorAccess access ? access : null;
    }

    @Unique
    private boolean isExterior(ChunkAccess chunk) {
        RingWorldGeneratorAccess access = ringworld$access();
        if (access == null) return false;
        RingGeometry geometry = access.ringworld$getGeometry();
        return geometry != null && RingGenerationBoundary.isExterior(chunk, geometry);
    }
}
