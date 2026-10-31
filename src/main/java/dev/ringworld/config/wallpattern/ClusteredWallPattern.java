package dev.ringworld.config.wallpattern;

import dev.ringworld.config.WallSettings;
import dev.ringworld.world.wall.RingWallPattern;

public class ClusteredWallPattern extends RingWallPattern {
    public ClusteredWallPattern() {
        super("clustered");
    }

    @Override
    public int getRoll(WallSettings settings, int x, int canonicalX, int y, int minY, int depth, int circumference, long worldSeed, int total, int fine, int coarse) {
        return RingWallPattern.blend(coarse, fine, 72, total);
    }
}
