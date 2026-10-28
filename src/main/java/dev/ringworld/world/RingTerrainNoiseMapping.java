package dev.ringworld.world;

/**
 * Versioned horizontal coordinate embedding used by Overworld terrain noise.
 */
public final class RingTerrainNoiseMapping {
    /**
     * Historical mapping shipped by the public alphas; retained for existing worlds only.
     */
    public static final int LEGACY_AXIAL = 1;
    /**
     * Orthogonal annular embedding used by newly created worlds.
     */
    public static final int ANNULAR = 2;
    /**
     * Annular density plus periodic surface-system and carver identities.
     */
    public static final int ANNULAR_COMPLETE = 3;
    /**
     * Complete annular mapping including vanilla's direct {@code BlendedNoise} sampler.
     */
    public static final int ANNULAR_COMPLETE_V2 = 4;
    public static final int CURRENT = ANNULAR_COMPLETE_V2;

    /**
     * Conservative allowance for structure and column queries beside the finite band.
     */
    public static final int QUERY_MARGIN_BLOCKS = 64;
    /**
     * Avoid the severe compression that occurs as an annular radius approaches zero.
     */
    public static final double MINIMUM_NOISE_RADIUS_BLOCKS = 32.0;

    public static int forSettingsFormat(int settingsFormat) {
        return settingsFormat >= 3 ? CURRENT : LEGACY_AXIAL;
    }

    public static int requireSupported(int mapping) {
        if (mapping != LEGACY_AXIAL && mapping != ANNULAR && mapping != ANNULAR_COMPLETE && mapping != ANNULAR_COMPLETE_V2) {
            throw new IllegalArgumentException("unsupported RingWorld terrain-noise mapping " + mapping);
        }
        return mapping;
    }

    /**
     * Stable diagnostic name for logs and the client debug HUD.
     */
    public static String diagnosticName(int mapping) {
        return switch (requireSupported(mapping)) {
            case LEGACY_AXIAL -> "legacy-axial";
            case ANNULAR -> "annular-v1";
            case ANNULAR_COMPLETE -> "annular-complete";
            case ANNULAR_COMPLETE_V2 -> "annular-complete-v2";
            default -> throw new AssertionError("validated terrain-noise mapping " + mapping);
        };
    }

    public static double minimumSampledRadius(RingGeometry geometry) {
        return geometry.radius() + geometry.minWidthZ() - QUERY_MARGIN_BLOCKS;
    }

    /**
     * Keeps one carver source identity when generation views it through either seam chart.
     */
    public static int carverSeedChunkX(RingGeometry geometry, int mapping, int sourceChunkX) {
        requireSupported(mapping);
        return mapping >= ANNULAR_COMPLETE ? Math.floorMod(sourceChunkX, geometry.circumferenceChunks()) : sourceChunkX;
    }
}
