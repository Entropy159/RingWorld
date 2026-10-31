package dev.ringworld.world.wall;

import dev.ringworld.config.WallSettings;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;

/**
 * Pure deterministic sampling for periodic rim materials and top-edge decay.
 */
public final class RingWallPattern {
    public static final long MATERIAL_SALT = 0x52494D5F4D41544CL;
    public static final long MATERIAL_FINE_SALT = 0x46494E455F4D4154L;
    public static final long MATERIAL_COARSE_SALT = 0x434F415253454D41L;
    public static final long DECAY_SALT = 0x52494D5F44454341L;
    public static final long DECAY_DEPTH_SALT = 0x44454341595F4450L;

    public static int materialRoll(WallSettings style, int x, int y, int depth, int circumference, long worldSeed) {
        int canonicalX = Math.floorMod(x, circumference);
        int total = style.getTotalRolls();
        int fine = materialNoise(worldSeed, MATERIAL_FINE_SALT, canonicalX, y, depth, style.palette(), total);
        int coarse = materialNoise(worldSeed, MATERIAL_COARSE_SALT, Math.floorDiv(canonicalX, 7), Math.floorDiv(y, 5), Math.floorDiv(depth, 2), style.palette(), total);

        return style.pattern().materialRoll(new Info(style, x, y, depth, circumference, worldSeed, canonicalX, total, fine, coarse));
    }

    /**
     * Number of blocks removed downward from one column of the wall top.
     * Depth is measured inward from the outer face, allowing the broken edge
     * to vary across a thick rim instead of cutting identical slots through it.
     */
    public static int topCollapseDepth(WallSettings style, int x, int depth, int circumference, long worldSeed) {
        if (depth < 0) throw new IllegalArgumentException("wall depth must be non-negative");
        if (style.pattern() == WallSettings.Pattern.ENGINEERED) {
            return RingEngineeredWallPattern.collapseDepth(style, x, depth, circumference, worldSeed);
        }
        if (style.decay() == 0) return 0;
        int canonicalX = Math.floorMod(x, circumference);
        // Collapse follows correlated structural weakness, not independent block
        // rolls: broad failed bays contain smaller fractures and chipped edges.
        double coarse = smoothNoise2d(worldSeed ^ 0x434F415253455F44L, canonicalX, depth, 32, 8, circumference);
        double medium = smoothNoise2d(worldSeed ^ 0x4D454449554D5F44L, canonicalX, depth, 8, 3, circumference);
        double fracture = smoothNoise2d(worldSeed, canonicalX, depth, 4, 2, circumference);
        double exposedFace = style.thickness() == 1 ? 1.0 : Math.abs(depth * 2.0 / (style.thickness() - 1) - 1.0);
        double noise = Math.min(1.0, coarse * 0.48 + medium * 0.34 + fracture * 0.18 + exposedFace * 0.07);
        double threshold = 1.0 - style.decay() / 100.0 * 0.88;
        if (noise <= threshold) return 0;
        int maximum = Math.max(1, 2 + style.decay() * 30 / 100);
        double severity = (noise - threshold) / Math.max(0.01, 1.0 - threshold);
        double depthNoise = smoothNoise2d(worldSeed ^ DECAY_DEPTH_SALT, canonicalX, depth, 16, 4, circumference);
        int collapse = 1 + (int) Math.floor(Math.pow(severity, 0.72) * (0.42 + depthNoise * 0.58) * maximum);
        return Math.min(maximum, collapse);
    }

    public static boolean blockPresent(WallSettings style, int x, int y, int depth, int wallTopExclusive, int circumference, long worldSeed) {
        return y < wallTopExclusive - topCollapseDepth(style, x, depth, circumference, worldSeed);
    }

    private static double unitNoise2d(long seed, int x, int depth) {
        long value = mix(seed ^ DECAY_SALT ^ (long) x * 0x9E3779B97F4A7C15L ^ (long) depth * 0xD1B54A32D192ED03L);
        return (value >>> 11) * 0x1.0p-53;
    }

    /**
     * Interpolated scalar noise whose X axis closes exactly at the ring seam.
     * For a circumference divisible by {@code xScale}, this retains the
     * established fixed-width sampling. Otherwise, ceiling cell count spreads
     * the final partial interval across the complete circumference instead of
     * introducing a second, discontinuous wrap within it.
     */
    static double smoothNoise2d(long seed, int x, int depth, int xScale, int depthScale, int circumference) {
        int canonicalX = Math.floorMod(x, circumference);
        int xCells = ceilDiv(circumference, xScale);
        double cellPosition = canonicalX * (double) xCells / circumference;
        int x0 = (int) Math.floor(cellPosition);
        int x1 = (x0 + 1) % xCells;
        int d0 = Math.floorDiv(depth, depthScale);
        int d1 = d0 + 1;
        double tx = smoothStep(cellPosition - x0);
        double td = smoothStep(Math.floorMod(depth, depthScale) / (double) depthScale);
        double low = lerp(unitNoise2d(seed, x0, d0), unitNoise2d(seed, x1, d0), tx);
        double high = lerp(unitNoise2d(seed, x0, d1), unitNoise2d(seed, x1, d1), tx);
        return lerp(low, high, td);
    }

    private static int ceilDiv(int value, int divisor) {
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }

    private static double smoothStep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    public static int panelRoll(WallSettings style, int canonicalX, int y, int depth, long worldSeed, int fine, int total) {
        int region = Math.floorDiv(canonicalX, 67);
        int panelWidth = 11 + materialNoise(worldSeed, MATERIAL_COARSE_SALT, region, depth, 3, style.palette(), total) % 13;
        int xOffset = materialNoise(worldSeed, MATERIAL_SALT, region, depth, 4, style.palette(), total) % panelWidth;
        int panelX = Math.floorMod(canonicalX + xOffset, panelWidth);
        int panel = Math.floorDiv(canonicalX + xOffset, panelWidth);
        int courseHeight = 8 + materialNoise(worldSeed, MATERIAL_COARSE_SALT, panel, depth, 5, style.palette(), total) % 11;
        int yOffset = materialNoise(worldSeed, MATERIAL_SALT, panel, depth, 6, style.palette(), total) % courseHeight;
        boolean rib = panelX == 0 || (panelX == panelWidth - 1 && materialNoise(worldSeed, MATERIAL_FINE_SALT, panel, Math.floorDiv(y, 5), depth, style.palette(), total) < 74) || Math.floorMod(y + yOffset, courseHeight) == 0;
        if (rib) {
            int wear = materialNoise(worldSeed, MATERIAL_FINE_SALT, canonicalX, y, depth + 17, style.palette(), total);
            return Math.clamp(76 + wear / 4, 0, total);
        }
        int body = materialNoise(worldSeed, MATERIAL_SALT, panel, Math.floorDiv(y + yOffset, courseHeight), depth, style.palette(), total);
        return blend(body, fine, 70, total);
    }

    public static int materialNoise(long seed, long salt, int x, int y, int depth, HashMap<BlockState, Integer> palette, int total) {
        long value = seed ^ salt ^ (long) x * 0x9E3779B97F4A7C15L ^ (long) y * 0xC2B2AE3D27D4EB4FL ^ (long) depth * 0x165667B19E3779F9L ^ (long) palette.hashCode() << 41;
        return (int) Long.remainderUnsigned(mix(value), total);
    }

    /**
     * Coarse weight is expressed as a percentage; fine noise breaks its edges.
     */
    public static int blend(int coarse, int fine, int coarseWeight, int total) {
        return Math.clamp(((long) coarse * coarseWeight + (long) fine * (total - coarseWeight)) / total, 0, total);
    }

    public static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    public record Info(WallSettings style, int x, int y, int depth, int circumference, long worldSeed, int canonicalX, int totalRolls, int fine, int coarse) {

    }
}
