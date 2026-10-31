package dev.ringworld.world.wall;

import dev.ringworld.config.WallSettings;
import dev.ringworld.world.RingGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Boundary operations performed during chunk generation, never live ticks.
 */
public final class RingGenerationBoundary {
    /**
     * Increment when rim placement/material semantics change.
     */
    public static final int RIM_STYLE_VERSION = 3;

    /**
     * Exclusive intrinsic Y immediately above the generated rim, clamped to
     * the owning world's vertical extent. Terrain above this bound remains
     * vanilla terrain; rim installation deliberately does not erase it.
     */
    public static int wallTopExclusive(int worldMinY, int worldHeight, int wallHeightBlocks) {
        if (worldHeight <= 0) throw new IllegalArgumentException("world height must be positive");
        if (wallHeightBlocks < 0) throw new IllegalArgumentException("wall height must be non-negative");
        long worldTopExclusive = (long) worldMinY + worldHeight;
        long requestedTopExclusive = (long) worldMinY + wallHeightBlocks;
        return (int) Math.min(requestedTopExclusive, worldTopExclusive);
    }

    public static boolean isExterior(ChunkAccess chunk, RingGeometry geometry) {
        return isExterior(chunk.getPos(), geometry);
    }

    public static boolean isExterior(ChunkPos pos, RingGeometry geometry) {
        return pos.getMaxBlockZ() < geometry.minWidthZ() || pos.getMinBlockZ() > geometry.maxWidthZ();
    }

    public static boolean containsRim(ChunkAccess chunk, RingGeometry geometry) {
        ChunkPos pos = chunk.getPos();
        return containsZ(pos, geometry.minWidthZ()) || containsZ(pos, geometry.maxWidthZ());
    }

    private static boolean containsZ(ChunkPos pos, int z) {
        return z >= pos.getMinBlockZ() && z <= pos.getMaxBlockZ();
    }

    /**
     * Removes only cross-chunk feature spillover; noise generation is skipped entirely.
     */
    public static void clearExterior(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section.hasOnlyAir()) continue;
            int sectionBottomY = chunk.getMinY() + sectionIndex * 16;
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        if (section.getBlockState(localX, localY, localZ).isAir()) continue;
                        mutable.set(pos.getMinBlockX() + localX, sectionBottomY + localY, pos.getMinBlockZ() + localZ);
                        chunk.removeBlockEntity(mutable);
                        chunk.setBlockState(mutable, Blocks.AIR.defaultBlockState(), 0);
                    }
                }
            }
        }
    }

    /**
     * Installs the saved deterministic wall style after features have finished.
     */
    public static void installRim(ChunkAccess chunk, RingGeometry geometry, int wallHeightBlocks, WallSettings style, long worldSeed) {
        if (wallHeightBlocks <= 0) return;
        ChunkPos pos = chunk.getPos();
        int firstZ = Math.max(pos.getMinBlockZ(), geometry.minWidthZ());
        int lastZ = Math.min(pos.getMaxBlockZ(), geometry.maxWidthZ());
        boolean intersectsStyledRim = false;
        for (int z = firstZ; z <= lastZ; z++) {
            if (rimDepthAtZ(geometry, style, z) >= 0) {
                intersectsStyledRim = true;
                break;
            }
        }
        if (!intersectsStyledRim) return;
        int wallTopExclusive = wallTopExclusive(chunk.getMinY(), chunk.getHeight(), wallHeightBlocks);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
            for (int y = chunk.getMinY(); y < wallTopExclusive; y++) {
                for (int z = firstZ; z <= lastZ; z++) {
                    int depth = rimDepthAtZ(geometry, style, z);
                    if (depth < 0) continue;
                    mutable.set(x, y, z);
                    chunk.removeBlockEntity(mutable);
                    if (RingWallPattern.blockPresent(style, x, y, depth, wallTopExclusive, geometry.circumference(), worldSeed)) {
                        chunk.setBlockState(mutable, texturedRimBlock(style, x, y, depth, geometry.circumference(), worldSeed), 0);
                    } else {
                        chunk.setBlockState(mutable, Blocks.AIR.defaultBlockState(), 0);
                    }
                }
            }
        }
    }

    /**
     * Replace only natural bedrock in the lowest layer; do not refill mined blocks.
     */
    public static void installUnderside(ChunkAccess chunk, RingGeometry geometry, WallSettings style, long seed) {
        var cp = chunk.getPos();
        int y = chunk.getMinY();
        var pos = new BlockPos.MutableBlockPos();
        for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++) {
            for (int z = Math.max(cp.getMinBlockZ(), geometry.minWidthZ()); z <= Math.min(cp.getMaxBlockZ(), geometry.maxWidthZ()); z++) {
                pos.set(x, y, z);
                if (!chunk.getBlockState(pos).is(Blocks.BEDROCK)) continue;
                // Fold the wall's vertical pattern across the underside from each rim.
                int patternY = y + Math.min(z - geometry.minWidthZ(), geometry.maxWidthZ() - z);
                chunk.setBlockState(pos, texturedRimBlock(style, x, patternY, 0, geometry.circumference(), seed), 0);
            }
        }
    }

    /**
     * Returns inward rim depth for a band Z, or -1 when it is playable interior.
     */
    public static int rimDepthAtZ(RingGeometry geometry, WallSettings style, int z) {
        int lowDepth = z - geometry.minWidthZ();
        if (lowDepth >= 0 && lowDepth < style.thickness()) return lowDepth;
        int highDepth = geometry.maxWidthZ() - z;
        return highDepth >= 0 && highDepth < style.thickness() ? highDepth : -1;
    }

    private static BlockState texturedRimBlock(WallSettings style, int x, int y, int depth, int circumference, long worldSeed) {
        int roll = RingWallPattern.materialRoll(style, x, y, depth, circumference, worldSeed);
        return styledRimBlockForRoll(style, roll);
    }

    public static BlockState styledRimBlockForRoll(WallSettings style, int roll) {
        return style.getFromRoll(roll);
    }

}
