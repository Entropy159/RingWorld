package dev.ringworld.world;

import dev.ringworld.Constants;
import dev.ringworld.world.atlas.RingTerrainAtlas;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, allocation-safe validation and cost report for a prospective
 * RingWorld layout.
 *
 * <p>{@link RingGeometry} accepts structurally useful small fixtures for pure
 * topology and atlas tests. This report defines whether that geometry is safe
 * to use as a complete playable Overworld.</p>
 */
public record RingDimensionReport(RingGeometry geometry, int rimThicknessBlocks, int playableInteriorBlocks,
                                  long playableInteriorAreaBlocks, int worldBottomY, int worldTopYExclusive,
                                  int wallHeightBlocks, int wallTopYExclusive, int cloudBaseY,
                                  double radialClearanceAtHighestPlane, double oppositeAngularWidthDegrees,
                                  long canonicalChunkCount, int atlasColumns, int atlasRows, long atlasCellCount,
                                  long estimatedAtlasBytes, long estimatedNoiseCoordinateBytes,
                                  RingDimensionCostEstimate costEstimate, List<String> errors, List<String> warnings) {

    public static String atlasLimitMessage(long cells) {
        return "Ring Larger than Max Atlas Size: terrain atlas requires " + cells + " cells; current limit is " + Constants.MAX_ATLAS_CELLS + ". Reduce Around or Across until the ring fits within this limit.";
    }

    public RingDimensionReport {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public static RingDimensionReport forVanillaOverworld(RingGeometry geometry, int wallHeightBlocks) {
        return forVanillaOverworld(geometry, wallHeightBlocks, RingGenerationBoundary.RIM_THICKNESS);
    }

    public static RingDimensionReport forVanillaOverworld(RingGeometry geometry, int wallHeightBlocks, int rimThicknessBlocks) {
        return forVanillaOverworld(geometry, wallHeightBlocks, rimThicknessBlocks, RingTerrainAtlas.SAMPLE_STEP_BLOCKS);
    }

    public static RingDimensionReport forVanillaOverworld(RingGeometry geometry, int wallHeightBlocks, int rimThicknessBlocks, int atlasSampleStepBlocks) {
        return evaluate(geometry, wallHeightBlocks, Constants.DEFAULT_MIN_Y, Constants.DEFAULT_MAX_Y, rimThicknessBlocks, atlasSampleStepBlocks);
    }

    public static RingDimensionReport evaluate(RingGeometry geometry, int wallHeightBlocks, int worldBottomY, int worldTopYExclusive, int rimThicknessBlocks, int atlasSampleStepBlocks) {
        if (worldTopYExclusive <= worldBottomY) {
            throw new IllegalArgumentException("world top must be above world bottom");
        }
        if (rimThicknessBlocks <= 0) {
            throw new IllegalArgumentException("rim thickness must be positive");
        }
        if (atlasSampleStepBlocks <= 0 || 16 % atlasSampleStepBlocks != 0) {
            throw new IllegalArgumentException("atlas sample step must divide one chunk");
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        int wallTopYExclusive;
        int cloudBaseY;
        try {
            wallTopYExclusive = Math.addExact(worldBottomY, wallHeightBlocks);
            cloudBaseY = Math.addExact(wallTopYExclusive, Constants.CLOUD_CLEARANCE_BLOCKS);
        } catch (ArithmeticException exception) {
            wallTopYExclusive = Integer.MAX_VALUE;
            cloudBaseY = Integer.MAX_VALUE;
            errors.add("wall/cloud height arithmetic exceeds the integer coordinate range");
        }

        if (wallHeightBlocks < Constants.MIN_WALL_HEIGHT_BLOCKS) {
            errors.add("wall height must be at least " + Constants.MIN_WALL_HEIGHT_BLOCKS + " blocks");
        }
        if (wallTopYExclusive > worldTopYExclusive) {
            errors.add("wall top Y=" + wallTopYExclusive + " exceeds Overworld top Y=" + worldTopYExclusive);
        }
        if (cloudBaseY > worldTopYExclusive) {
            errors.add("cloud base Y=" + cloudBaseY + " exceeds Overworld top Y=" + worldTopYExclusive);
        }

        int playableInterior = geometry.widthBlocks() - rimThicknessBlocks * 2;
        long playableInteriorArea = Math.multiplyExact((long) geometry.circumferenceBlocks(), playableInterior);
        if (playableInterior < Constants.MIN_PLAYABLE_INTERIOR_BLOCKS) {
            errors.add("width leaves only " + playableInterior + " interior blocks after both " + rimThicknessBlocks + "-block rims");
        }

        int highestPhysicalPlane = Math.max(worldTopYExclusive, cloudBaseY);
        double radialClearance = geometry.physicalRadiusAt(highestPhysicalPlane);
        int requiredCircumference = minimumCircumferenceBlocks(highestPhysicalPlane, RingGeometry.SURFACE_Y, Constants.MIN_RADIAL_CLEARANCE_BLOCKS);
        if (radialClearance < Constants.MIN_RADIAL_CLEARANCE_BLOCKS) {
            errors.add("circumference leaves only " + format(radialClearance) + " radial blocks at Y=" + highestPhysicalPlane + "; at least " + Constants.MIN_RADIAL_CLEARANCE_BLOCKS + " are required (minimum aligned circumference " + requiredCircumference + ")");
        }

        double minimumNoiseRadius = RingTerrainNoiseMapping.minimumSampledRadius(geometry);
        if (minimumNoiseRadius < RingTerrainNoiseMapping.MINIMUM_NOISE_RADIUS_BLOCKS) {
            errors.add("periodic terrain-noise band reaches radius " + format(minimumNoiseRadius) + " blocks after its query margin; at least " + format(RingTerrainNoiseMapping.MINIMUM_NOISE_RADIUS_BLOCKS) + " blocks are required");
        }

        if (geometry.widthBlocks() > Constants.MAX_AXIS_BLOCKS) {
            errors.add("width exceeds the current " + Constants.MAX_AXIS_BLOCKS + "-block technical limit");
        }
        if (geometry.circumferenceBlocks() > Constants.MAX_AXIS_BLOCKS) {
            errors.add("circumference exceeds the current " + Constants.MAX_AXIS_BLOCKS + "-block technical limit");
        }

        long chunks = Math.multiplyExact((long) geometry.circumferenceChunks(), geometry.widthChunks());
        int atlasColumns = Math.toIntExact(divideCeil(geometry.circumferenceBlocks(), atlasSampleStepBlocks));
        int atlasRows = Math.toIntExact(divideCeil(geometry.widthBlocks(), atlasSampleStepBlocks));
        long atlasCells = Math.multiplyExact((long) atlasColumns, atlasRows);
        long atlasBytes = Math.multiplyExact(atlasCells, RingTerrainAtlas.ESTIMATED_BYTES_PER_CELL);
        long noiseCoordinateBytes = Math.multiplyExact((long) geometry.circumferenceBlocks(), 16L);
        RingDimensionCostEstimate costEstimate = RingDimensionCostEstimate.estimate(geometry, atlasSampleStepBlocks);
        if (atlasCells > Constants.MAX_ATLAS_CELLS) {
            errors.add(atlasLimitMessage(atlasCells));
        } else if (atlasCells > Constants.WARN_ATLAS_CELLS) {
            warnings.add("terrain atlas requires " + atlasCells + " cells (approximately " + atlasBytes + " raw bytes)");
        }
        if (chunks > Constants.WARN_CANONICAL_CHUNKS) {
            warnings.add("complete-ring pregeneration requires " + chunks + " canonical chunks");
        }
        if (costEstimate.estimatedPregenerationSeconds() > Constants.WARN_PREGENERATION_SECONDS || costEstimate.estimatedGeneratedWorldBytes() > Constants.WARN_GENERATED_WORLD_BYTES) {
            warnings.add("measured-reference full generation is about " + formatDuration(costEstimate.estimatedPregenerationSeconds()) + " and " + formatGiB(costEstimate.estimatedGeneratedWorldBytes()) + " GiB of generated world data");
        }

        double angularWidth = geometry.oppositeAngularWidthDegrees(0.0);
        if (angularWidth < 2.5) {
            warnings.add("opposite ring width is only " + format(angularWidth) + " degrees and may look unusually narrow");
        } else if (angularWidth > 120.0) {
            warnings.add("opposite ring width is " + format(angularWidth) + " degrees and may dominate the sky");
        }

        return new RingDimensionReport(geometry, rimThicknessBlocks, playableInterior, playableInteriorArea, worldBottomY, worldTopYExclusive, wallHeightBlocks, wallTopYExclusive, cloudBaseY, radialClearance, angularWidth, chunks, atlasColumns, atlasRows, atlasCells, atlasBytes, noiseCoordinateBytes, costEstimate, errors, warnings);
    }

    /**
     * Full walking lap at the named vanilla normal-walking reference speed.
     */
    public double normalWalkingLapSeconds() {
        return geometry.circumferenceBlocks() / Constants.NORMAL_WALKING_SPEED_BLOCKS_PER_SECOND;
    }

    /**
     * Reference-surface diameter; kept here for UI/report consumers.
     */
    public double diameterBlocks() {
        return geometry.diameter();
    }

    /**
     * Whether generation or atlas workload crosses a named cost threshold.
     */
    public boolean hasHighGenerationCost() {
        return canonicalChunkCount > Constants.WARN_CANONICAL_CHUNKS || atlasCellCount > Constants.WARN_ATLAS_CELLS || costEstimate.estimatedPregenerationSeconds() > Constants.WARN_PREGENERATION_SECONDS || costEstimate.estimatedGeneratedWorldBytes() > Constants.WARN_GENERATED_WORLD_BYTES;
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public void requireValid() {
        if (!isValid()) {
            throw new IllegalArgumentException("Invalid RingWorld dimensions: " + String.join("; ", errors));
        }
    }

    public static int minimumCircumferenceBlocks(int highestPhysicalY, double surfaceReferenceY, double radialClearanceBlocks) {
        if (!(radialClearanceBlocks >= 0.0) || !Double.isFinite(radialClearanceBlocks)) {
            throw new IllegalArgumentException("radial clearance must be finite and non-negative");
        }
        double requiredRadius = highestPhysicalY - surfaceReferenceY + radialClearanceBlocks;
        if (!(requiredRadius > 0.0) || !Double.isFinite(requiredRadius)) {
            throw new IllegalArgumentException("required physical radius must be finite and positive");
        }
        double blocks = Math.ceil(Math.PI * 2.0 * requiredRadius);
        if (blocks > Integer.MAX_VALUE - 15.0) {
            throw new IllegalArgumentException("minimum circumference exceeds integer coordinates");
        }
        return Math.toIntExact(((long) blocks + 15L) / 16L * 16L);
    }

    private static long divideCeil(int value, int divisor) {
        return ((long) value + divisor - 1L) / divisor;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String formatDuration(long seconds) {
        long hours = seconds / 3600L;
        long minutes = seconds % 3600L / 60L;
        return hours > 0L ? hours + "h " + minutes + "m" : minutes + "m";
    }

    private static String formatGiB(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
