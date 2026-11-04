package dev.ringworld.world;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Shared immutable cylindrical coordinate lookup used by every chunk sampler.
 */
public final class RingNoiseCoordinates {
    private static final int MAX_PRECOMPUTED_CIRCUMFERENCE = 1048576;
    private static final ConcurrentMap<RingGeometry, RingNoiseCoordinates> CACHE = new ConcurrentHashMap<>();

    private final int circumference;
    private final double radius;
    private final double[] sine;
    private final double[] cosine;

    public static RingNoiseCoordinates forGeometry(RingGeometry geometry) {
        return CACHE.computeIfAbsent(geometry, RingNoiseCoordinates::new);
    }

    /**
     * Drops per-layout lookup arrays when the owning Overworld unloads.
     */
    public static void clearCache() {
        CACHE.clear();
    }

    private RingNoiseCoordinates(RingGeometry geometry) {
        circumference = geometry.circumference();
        radius = geometry.radius();
        if (circumference <= MAX_PRECOMPUTED_CIRCUMFERENCE) {
            sine = new double[circumference];
            cosine = new double[circumference];
            for (int x = 0; x < circumference; x++) {
                double angle = Math.PI * 2.0 * x / circumference;
                sine[x] = Math.sin(angle);
                cosine[x] = Math.cos(angle);
            }
        } else {
            sine = null;
            cosine = null;
        }
    }

    /**
     * Horizontal noise X. The annular mapping deliberately depends on intrinsic Z.
     */
    public int noiseX(int sourceX, int sourceZ) {
        int canonicalX = Math.floorMod(sourceX, circumference);
        double sin = sine != null ? sine[canonicalX] : Math.sin(angle(canonicalX));
        return rounded((radius + sourceZ) * sin);
    }

    /**
     * Horizontal noise Z.
     */
    public int noiseZ(int sourceX, int sourceZ) {
        int canonicalX = Math.floorMod(sourceX, circumference);
        double cos = cosine != null ? cosine[canonicalX] : Math.cos(angle(canonicalX));
        return rounded((radius + sourceZ) * cos);
    }

    private double angle(int canonicalX) {
        return Math.PI * 2.0 * canonicalX / circumference;
    }

    private static int rounded(double value) {
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("Ring dimensions exceed vanilla noise coordinate range");
        }
        return (int) Math.round(value);
    }
}
