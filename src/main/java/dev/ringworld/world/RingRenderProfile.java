package dev.ringworld.world;

import dev.ringworld.world.atlas.RingAtlasFidelity;

/**
 * One dimension-aware visual/resource profile shared by Java renderers and
 * shader globals. Coverage distances include intrinsic horizontal blocks and
 * canonical vertical separation; material/detail distances remain horizontal.
 */
public record RingRenderProfile(int visualProfileVersion, double requestedViewDistanceBlocks,
                                double effectiveViewDistanceBlocks, double halfCircumferenceBlocks,
                                double liveFadeStartBlocks, double liveFadeEndBlocks, double proxyFadeStartBlocks,
                                double proxyFadeEndBlocks, double detailStartBlocks, double detailEndBlocks,
                                double revealNear, double revealFar, double hazeNear, double hazeFar,
                                double hazeExponent, double cloudFadeStartBlocks, double cloudFadeEndBlocks,
                                int textureColumns, int textureRows, int circumferenceSegments, int widthBands,
                                long vertexCount, double textureBlocksPerTexelX, double textureBlocksPerTexelZ) {

    /**
     * Increment when visual-policy semantics change enough that comparison
     * captures need to identify a different profile.
     */
    public static final int VISUAL_PROFILE_VERSION = 7;
    public static final double LIVE_FADE_START_FACTOR = 0.90;
    public static final double LIVE_FADE_END_FACTOR = 1.02;
    public static final double PROXY_FADE_START_FACTOR = 0.68;
    public static final double PROXY_FADE_END_FACTOR = 0.98;
    public static final double DETAIL_START_FACTOR = 0.76;
    public static final double DETAIL_END_FACTOR = 1.25;
    /**
     * Retains the atlas's eight-block height sampling around the default
     * 16,384-block ring. The former 512-segment cap stretched each production
     * triangle across 32 blocks and exposed a visibly faceted proxy at short
     * render distances.
     */
    public static final int MAX_CIRCUMFERENCE_SEGMENTS = 2048;
    public static final int MAX_WIDTH_BANDS = 128;
    public static final int POSITION_TEXTURE_COLOR_VERTEX_BYTES = 24;
    public static final double REVEAL_NEAR = 0.82;
    public static final double REVEAL_FAR = 0.98;
    public static final double HAZE_NEAR = 0.04;
    public static final double HAZE_FAR = 0.16;
    public static final double HAZE_EXPONENT = 1.35;

    public static RingRenderProfile create(RingGeometry geometry, double viewDistanceBlocks) {
        return create(geometry, viewDistanceBlocks, RingAtlasFidelity.BALANCED);
    }

    public static RingRenderProfile create(RingGeometry geometry, double viewDistanceBlocks, RingAtlasFidelity fidelity) {
        if (fidelity == null) throw new IllegalArgumentException("Atlas fidelity is required");
        return create(geometry, viewDistanceBlocks, fidelity.maxTextureColumns(), fidelity.maxTextureRows(), fidelity.meshStepBlocks());
    }

    /**
     * Explicit client-side display budget; never changes saved Atlas fidelity.
     */
    public static RingRenderProfile create(RingGeometry geometry, double viewDistanceBlocks, int maxColumns, int maxRows, int meshStep) {
        if (maxColumns <= 0 || maxRows <= 0 || meshStep <= 0) {
            throw new IllegalArgumentException("positive display budget required");
        }
        if (!Double.isFinite(viewDistanceBlocks) || viewDistanceBlocks <= 0.0) {
            throw new IllegalArgumentException("view distance must be finite and positive");
        }
        double half = geometry.circumferenceBlocks() * 0.5;
        double effective = Math.clamp(viewDistanceBlocks, 16.0, half);
        double liveStart = effective * LIVE_FADE_START_FACTOR;
        double liveEnd = Math.clamp(liveStart + 8.0, effective * LIVE_FADE_END_FACTOR, half);
        double proxyStart = effective * PROXY_FADE_START_FACTOR;
        double proxyEnd = Math.clamp(proxyStart + 16.0, effective * PROXY_FADE_END_FACTOR, half);
        double detailStart = effective * DETAIL_START_FACTOR;
        double detailEnd = Math.clamp(detailStart + 16.0, effective * DETAIL_END_FACTOR, half);
        double cloudEnd = Math.min(effective * 0.82, geometry.circumferenceBlocks() * 0.12);
        double cloudStart = Math.clamp(cloudEnd * 0.55, 8.0, cloudEnd);

        int textureColumns = Math.min(geometry.circumferenceBlocks(), maxColumns);
        int textureRows = Math.min(geometry.widthBlocks(), maxRows);
        int circumferenceSegments = Math.clamp(maxColumns / 2, MAX_CIRCUMFERENCE_SEGMENTS, divideCeil(geometry.circumferenceBlocks(), meshStep));
        int widthBands = Math.min(divideCeil(geometry.widthBlocks(), meshStep), MAX_WIDTH_BANDS);
        long vertices = Math.multiplyExact(Math.multiplyExact((long) circumferenceSegments, widthBands), 6L);

        return new RingRenderProfile(VISUAL_PROFILE_VERSION, viewDistanceBlocks, effective, half, liveStart, liveEnd, proxyStart, proxyEnd, detailStart, detailEnd, REVEAL_NEAR, REVEAL_FAR, HAZE_NEAR, HAZE_FAR, HAZE_EXPONENT, cloudStart, cloudEnd, textureColumns, textureRows, circumferenceSegments, widthBands, vertices, (double) geometry.circumferenceBlocks() / textureColumns, (double) geometry.widthBlocks() / textureRows);
    }

    public boolean wholeRingViewRequested() {
        return requestedViewDistanceBlocks >= halfCircumferenceBlocks;
    }

    /**
     * RGBA8 base texture plus the renderer's complete filtered mip chain.
     */
    public long estimatedGpuTextureBytes() {
        int width = textureColumns;
        int height = textureRows;
        long texels = 0L;
        while (true) {
            texels = Math.addExact(texels, Math.multiplyExact((long) width, height));
            if (Math.min(width, height) <= 1) break;
            width = Math.max(1, width >> 1);
            height = Math.max(1, height >> 1);
        }
        return Math.multiplyExact(texels, 4L);
    }

    public long estimatedGpuMeshBytes() {
        return Math.multiplyExact(vertexCount, POSITION_TEXTURE_COLOR_VERTEX_BYTES);
    }

    /**
     * Conservative peak for expanded ARGB pixels, float heights, and one
     * NativeImage upload at base resolution.
     */
    public long estimatedTextureBuildScratchBytes() {
        long texels = Math.multiplyExact((long) textureColumns, textureRows);
        return Math.multiplyExact(texels, 12L);
    }

    private static int divideCeil(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
