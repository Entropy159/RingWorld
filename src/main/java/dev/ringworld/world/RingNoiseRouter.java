package dev.ringworld.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import org.jspecify.annotations.NonNull;

import java.util.IdentityHashMap;

/**
 * Rewrites noise leaves before 26.3 compiles and caches their samplers.
 */
public final class RingNoiseRouter implements DfRewriteRule {
    private final RingGeometry geometry;
    private final int mapping;
    private final long seed;
    private final int seaLevel;
    private final NoiseRouter router;
    private final RingNoiseCoordinates coordinates;
    private final IdentityHashMap<DensityFunction, DensityFunction> rewritten = new IdentityHashMap<>();

    public RingNoiseRouter(RingGeometry geometry, int mapping, long seed, int seaLevel, NoiseRouter router) {
        this.geometry = geometry;
        this.mapping = mapping;
        this.seed = seed;
        this.seaLevel = seaLevel;
        this.router = router;
        coordinates = RingNoiseCoordinates.forGeometry(geometry, mapping);
    }

    public boolean matches(RingGeometry geometry, int mapping, long seed, int seaLevel, NoiseRouter router) {
        return this.geometry.equals(geometry) && this.mapping == mapping && this.seed == seed && this.seaLevel == seaLevel && this.router == router;
    }

    @Override
    public synchronized @NonNull DensityFunction rewrite(@NonNull DensityFunction original) {
        DensityFunction cached = rewritten.get(original);
        if (cached != null) return cached;
        DensityFunction input = DfRewriteRule.INLINE_REFERENCE.rewrite(original);
        DensityFunction result;
        if (input != original) result = rewrite(input);
        else if (input instanceof RingCoordinateDensityFunction || (mapping >= RingTerrainNoiseMapping.ANNULAR_COMPLETE_V2 && input instanceof BlendedNoise)) {
            // The whole noise leaf sees one transform, including its shift children.
            result = new Cylindrical(input, coordinates);
        } else result = input.rewriteChildren(this);
        rewritten.put(original, result);
        return result;
    }

    private record Cylindrical(DensityFunction input, RingNoiseCoordinates coordinates) implements DensityFunction {
        @Override
        public @NonNull DensitySampler compileSampler(@NonNull CompileContext compiler) {
            DensitySampler delegate = input.compileSampler(compiler);
            return new DensitySampler() {
                @Override
                public float sampleValue(@NonNull SamplerContext context, int x, int y, int z) {
                    return delegate.sampleValue(context, coordinates.noiseX(x, z), y, coordinates.noiseZ(x, z));
                }

                @Override
                public void sampleVolume(@NonNull SamplerContext context, @NonNull DensityBuffer output, @NonNull DensityVolume volume) {
                    int index = 0;
                    for (int z = 0; z < volume.sizeZ(); z++)
                        for (int x = 0; x < volume.sizeX(); x++) {
                            int sourceX = volume.blockX(x), sourceZ = volume.blockZ(z);
                            int mappedX = coordinates.noiseX(sourceX, sourceZ), mappedZ = coordinates.noiseZ(sourceX, sourceZ);
                            for (int y = 0; y < volume.sizeY(); y++)
                                output.set(index++, delegate.sampleValue(context, mappedX, volume.blockY(y), mappedZ));
                        }
                }
            };
        }

        @Override
        public @NonNull DensityFunction rewriteChildren(@NonNull DfRewriteRule rule) {
            return new Cylindrical(input.rewriteChildren(rule), coordinates);
        }

        @Override
        public @NonNull Interval range() {
            return input.range();
        }

        @Override
        public int domainAxes() {
            int axes = input.domainAxes();
            // Cylindrical X and Z both depend on the two intrinsic coordinates.
            return (axes & (AXIS_X | AXIS_Z)) == 0 ? axes : axes | AXIS_X | AXIS_Z;
        }

        @Override
        public @NonNull MapCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("Ring samplers are runtime-only");
        }
    }

}
