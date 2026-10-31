package dev.ringworld.config.wallpattern;

import dev.ringworld.config.WallSettings;
import dev.ringworld.world.wall.RingWallPattern;

public class StrataWallPattern extends RingWallPattern {
    public StrataWallPattern() {
        super("strata");
    }

    @Override
    public int getRoll(WallSettings settings, int x, int canonicalX, int y, int minY, int depth, int circumference, long worldSeed, int total, int fine, int coarse) {
        int section = Math.floorDiv(canonicalX, 19);
        int wave = RingWallPattern.materialNoise(worldSeed, RingWallPattern.MATERIAL_COARSE_SALT, section, depth, 2, settings.palette(), total) % 9 - 4;
        int bandHeight = 3 + RingWallPattern.materialNoise(worldSeed, RingWallPattern.MATERIAL_SALT, section, Math.floorDiv(y, 13), depth, settings.palette(), total) % 7;
        int band = Math.floorDiv(y + wave, bandHeight);
        int body = RingWallPattern.materialNoise(worldSeed, RingWallPattern.MATERIAL_SALT, Math.floorDiv(canonicalX, 11), band, depth, settings.palette(), total);
        return RingWallPattern.blend(body, fine, 68, total);
    }
}
