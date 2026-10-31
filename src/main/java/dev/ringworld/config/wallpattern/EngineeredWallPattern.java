package dev.ringworld.config.wallpattern;

import dev.ringworld.config.WallSettings;
import dev.ringworld.world.wall.RingWallPattern;

public class EngineeredWallPattern extends RingWallPattern {
    public EngineeredWallPattern() {
        super("engineered");
    }

    @Override
    public int getRoll(WallSettings settings, int x, int canonicalX, int y, int minY, int depth, int circumference, long worldSeed, int total, int fine, int coarse) {
        return RingWallPattern.panelRoll(settings, canonicalX, y, depth, worldSeed, fine, total);
    }

    @Override
    public int collapseDepth(WallSettings settings, int x, int depth, int circumference, long worldSeed) {
        Bay bay = bay(x, circumference);
        double broad = RingWallPattern.smoothNoise2d(worldSeed ^ 0x42524F4B454E4241L, x, depth, 24, 7, circumference);
        double fracture = RingWallPattern.smoothNoise2d(worldSeed ^ 0x4652414354555245L, x, depth, 5, 3, circumference);
        double wear = settings.decay() / 100.0;
        double weakness = broad * 0.7 + fracture * 0.3;
        double severity = Math.max(0, (weakness - (0.70 - wear * 0.48)) / 0.48);
        double distanceFromSupport = Math.min(bay.position(), bay.width() - bay.position());
        double structural = Math.min(1.0, 0.18 + distanceFromSupport / 6.0);
        int maximum = 2 + settings.decay() * 32 / 100;
        return Math.min(maximum, (int) Math.floor(severity * maximum * structural));
    }

    private static Bay bay(int x, int circumference) {
        if (circumference <= 0) throw new IllegalArgumentException("positive circumference required");
        int count = Math.max(1, (circumference + 12) / 24);
        double coordinate = (double) Math.floorMod(x, circumference) * count / circumference;
        double width = (double) circumference / count;
        return new Bay((int) coordinate, (coordinate - Math.floor(coordinate)) * width, width);
    }

    private record Bay(int index, double position, double width) {
    }
}
