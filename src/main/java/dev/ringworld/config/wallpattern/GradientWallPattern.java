package dev.ringworld.config.wallpattern;

import dev.ringworld.config.WallSettings;
import dev.ringworld.world.wall.RingWallPattern;

public class GradientWallPattern extends RingWallPattern {
    public GradientWallPattern() {
        super("gradient");
    }

    @Override
    public int getRoll(WallSettings settings, int x, int canonicalX, int y, int minY, int depth, int circumference, long worldSeed, int total, int fine, int coarse) {
        int normalY = y - minY;
        float height = Math.clamp(normalY / (float) settings.height(), 0, 1);
        int random = RingWallPattern.materialNoise(worldSeed, RingWallPattern.MATERIAL_FINE_SALT, canonicalX, y, depth, settings.palette(), 10000);
        int t = total - 1;
        double value = t * height;
        int lower = (int) value;
        double fraction = value - lower;
        return random < fraction * 10000 ? Math.min(t, lower + 1) : lower;
    }
}
