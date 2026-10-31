package dev.ringworld.world;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Pure destination bounds for portal lookup and creation in the finite Overworld band.
 */
public final class RingPortalDestinationBounds {
    /**
     * Vanilla {@code PortalForcer#createPortal} searches this far from its anchor.
     */
    public static final int CREATION_SEARCH_RADIUS = 16;
    /**
     * Covers the widest vanilla portal frame/foundation offset from a portal block.
     */
    public static final int FRAME_CLEARANCE = 3;

    public static int safePortalMinZ(RingGeometry geometry, int rimThicknessBlocks) {
        return geometry.minWidthZ() + rimThicknessBlocks + FRAME_CLEARANCE;
    }

    public static int safePortalMaxZ(RingGeometry geometry, int rimThicknessBlocks) {
        return geometry.maxWidthZ() - rimThicknessBlocks - FRAME_CLEARANCE;
    }

    public static int safeAnchorMinZ(RingGeometry geometry, int rimThicknessBlocks) {
        return safePortalMinZ(geometry, rimThicknessBlocks) + CREATION_SEARCH_RADIUS;
    }

    public static int safeAnchorMaxZ(RingGeometry geometry, int rimThicknessBlocks) {
        return safePortalMaxZ(geometry, rimThicknessBlocks) - CREATION_SEARCH_RADIUS;
    }

    public static BlockPos normalizeSearchAnchor(RingGeometry geometry, BlockPos target, int rimThicknessBlocks) {
        int minZ = safeAnchorMinZ(geometry, rimThicknessBlocks);
        int maxZ = safeAnchorMaxZ(geometry, rimThicknessBlocks);
        if (minZ > maxZ) {
            throw new IllegalArgumentException("ring width cannot contain a safe Nether portal search envelope");
        }
        int z = Math.clamp(target.getZ(), minZ, maxZ);
        return new BlockPos(geometry.wrapBlockX(target.getX()), target.getY(), z);
    }

    public static boolean isSafePortalBlock(RingGeometry geometry, BlockPos portalBlock, int rimThicknessBlocks) {
        return portalBlock.getZ() >= safePortalMinZ(geometry, rimThicknessBlocks) && portalBlock.getZ() <= safePortalMaxZ(geometry, rimThicknessBlocks);
    }

    /**
     * Flat POI queries centred on all three local X images expose either side of the seam.
     */
    public static List<BlockPos> periodicQueryAnchors(RingGeometry geometry, BlockPos normalizedAnchor) {
        int x = geometry.wrapBlockX(normalizedAnchor.getX());
        int circumference = geometry.circumference();
        return List.of(new BlockPos(x, normalizedAnchor.getY(), normalizedAnchor.getZ()), new BlockPos(x - circumference, normalizedAnchor.getY(), normalizedAnchor.getZ()), new BlockPos(x + circumference, normalizedAnchor.getY(), normalizedAnchor.getZ()));
    }

    /**
     * Periodic-X equivalent of vanilla's squared distance used to select a portal POI.
     */
    public static double periodicDistanceSquared(RingGeometry geometry, BlockPos anchor, BlockPos candidate) {
        double dx = geometry.shortestCircumferenceDelta(anchor.getX(), candidate.getX());
        double dy = candidate.getY() - anchor.getY();
        double dz = candidate.getZ() - anchor.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
