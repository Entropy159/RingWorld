package dev.ringworld.world.atlas;

import java.util.Optional;

/**
 * Small, allocation-light model for the in-game incomplete-Atlas indicator.
 */
public final class RingAtlasHudProgress {
    private static final String PREFIX = "Ring Atlas Generating: ";

    public static Optional<String> label(int presentCells, int totalCells) {
        if (totalCells <= 0 || presentCells < 0 || presentCells > totalCells) {
            throw new IllegalArgumentException("invalid Ring Atlas progress");
        }
        if (presentCells == totalCells) return Optional.empty();
        int percent = Math.min(99, (int) (presentCells * 100L / totalCells));
        return Optional.of(PREFIX + percent + "%");
    }
}
