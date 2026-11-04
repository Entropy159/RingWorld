package dev.ringworld.world;

import java.util.*;

/**
 * Pure periodic-X inputs shared by future raid-centre and wave-spawn adapters.
 */
public final class RingRaidSupport {
    /**
     * Inclusive canonical X range for a block or chunk lookup.
     */
    public record XWindow(int minX, int maxX) {
        public XWindow {
            if (minX > maxX) throw new IllegalArgumentException("window minimum must not exceed maximum");
        }
    }

    /**
     * Squared intrinsic distance whose X component crosses the seam locally.
     */
    public static double periodicDistanceSquared(RingGeometry geometry, double sourceX, double sourceY, double sourceZ, double targetX, double targetY, double targetZ) {
        double deltaX = geometry.shortestCircumferenceDelta(sourceX, targetX);
        double deltaY = targetY - sourceY;
        double deltaZ = targetZ - sourceZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    /**
     * Returns the three presentation query origins which expose canonical POIs
     * on both sides of the joined edge to vanilla's flat range search.
     */
    public static List<Integer> periodicQueryXs(RingGeometry geometry, int originX) {
        int canonical = geometry.wrapBlockX(originX);
        int circumference = geometry.circumference();
        return List.of(canonical, canonical - circumference, canonical + circumference);
    }

    /**
     * Splits an inclusive presentation-block range into one or two canonical storage windows.
     */
    public static List<XWindow> canonicalBlockWindows(RingGeometry geometry, int minBlockX, int maxBlockX) {
        if (minBlockX > maxBlockX) throw new IllegalArgumentException("window minimum must not exceed maximum");
        long width = (long) maxBlockX - minBlockX + 1L;
        int circumference = geometry.circumference();
        if (width >= circumference) return List.of(new XWindow(0, circumference - 1));

        int start = geometry.wrapBlockX(minBlockX);
        int endExclusive = start + (int) width;
        if (endExclusive <= circumference) return List.of(new XWindow(start, endExclusive - 1));
        return List.of(new XWindow(start, circumference - 1), new XWindow(0, endExclusive - circumference - 1));
    }
}
