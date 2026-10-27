package dev.ringworld.world;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the climate sampler matching RingWorld's wrapped biome router.
 */
public final class RingClimateSampler {
    /**
     * Mirrors RandomState's sampler-only visitor. NoiseChunk additionally
     * replaces cache/interpolation wrappers for performance; that does not
     * change the sampled climate mathematics used for biome selection.
     */
    public static Climate.Sampler create(NoiseRouter router, List<Climate.ParameterPoint> spawnTarget) {
        Map<DensityFunction, DensityFunction> unwrapped = new HashMap<>();
        DensityFunction.Visitor visitor = function -> unwrapped.computeIfAbsent(function, RingClimateSampler::unwrapSamplerFunction);
        return new Climate.Sampler(router.temperature().mapAll(visitor), router.vegetation().mapAll(visitor), router.continents().mapAll(visitor), router.erosion().mapAll(visitor), router.depth().mapAll(visitor), router.ridges().mapAll(visitor), spawnTarget);
    }

    private static DensityFunction unwrapSamplerFunction(DensityFunction function) {
        if (function instanceof DensityFunctions.HolderHolder(Holder<DensityFunction> function1)) return function1.value();
        if (function instanceof DensityFunctions.MarkerOrMarked marker) return marker.wrapped();
        return function;
    }
}
