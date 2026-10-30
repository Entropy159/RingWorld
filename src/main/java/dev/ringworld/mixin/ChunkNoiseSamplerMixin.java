package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingNoiseRouter;
import dev.ringworld.world.RingRandomStateAccess;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 26.3 routes terrain, climate, aquifer, ore and material functions here.
 */
@Mixin(RandomState.class)
abstract class ChunkNoiseSamplerMixin implements RingRandomStateAccess {
    @Unique
    private volatile RingNoiseRouter ringworld$mapper;

    @Override
    public synchronized void ringworld$configureNoise(RingGeometry geometry, int mapping, long seed, int seaLevel, NoiseRouter router) {
        if (ringworld$mapper != null && ringworld$mapper.matches(geometry, mapping, seed, seaLevel, router))
            return;
        ringworld$mapper = new RingNoiseRouter(geometry, mapping, seed, seaLevel, router);
    }

    @ModifyVariable(method = "getSampler", at = @At("HEAD"), argsOnly = true, name = "function")
    private DensityFunction ringworld$periodicFunction(DensityFunction function) {
        RingNoiseRouter mapper = ringworld$mapper;
        return mapper == null ? function : mapper.rewrite(function);
    }
}
