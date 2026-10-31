package dev.ringworld.config.wallpattern;

import dev.ringworld.config.WallSettings;
import dev.ringworld.world.wall.RingWallPattern;

public class MasonryWallPattern extends RingWallPattern {
    public MasonryWallPattern() {
        super("masonry");
    }

    @Override
    public int getRoll(WallSettings settings, int x, int canonicalX, int y, int minY, int depth, int circumference, long worldSeed, int total, int fine, int coarse) {
        int coarse2 = Math.floorDiv(y, 2);
        int brickWidth = 3 + RingWallPattern.materialNoise(worldSeed, RingWallPattern.MATERIAL_SALT, coarse2, depth, 0, settings.palette(), total) % 4;
        int offset = RingWallPattern.materialNoise(worldSeed, RingWallPattern.MATERIAL_COARSE_SALT, coarse2, depth, 1, settings.palette(), total) % brickWidth;
        int brick = Math.floorDiv(canonicalX + offset, brickWidth);
        int body = RingWallPattern.materialNoise(worldSeed, RingWallPattern.MATERIAL_SALT, brick, coarse2, depth, settings.palette(), total);
        return RingWallPattern.blend(body, fine, 76, total);
    }
}
