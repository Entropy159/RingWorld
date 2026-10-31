package dev.ringworld.config.wallpattern;

import dev.ringworld.config.WallSettings;
import dev.ringworld.world.wall.RingWallPattern;

public class HybridWallPattern extends RingWallPattern {
    public HybridWallPattern() {
        super("hybrid");
    }

    @Override
    public int getRoll(WallSettings settings, int x, int canonicalX, int y, int minY, int depth, int circumference, long worldSeed, int total, int fine, int coarse) {
        int clustered = RingWallPattern.blend(coarse, fine, 66, total);
        int panel = RingWallPattern.panelRoll(settings, canonicalX, y, depth, worldSeed, fine, total);
        int selector = RingWallPattern.materialNoise(worldSeed, RingWallPattern.MATERIAL_SALT, Math.floorDiv(canonicalX, 23), Math.floorDiv(y, 17), depth, settings.palette(), total);
        return selector < 28 ? panel : RingWallPattern.blend(clustered, panel, 82, total);
    }
}
