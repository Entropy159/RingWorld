package dev.ringworld.server.atlas.pregen;

/**
 * Shared player-load/ready-chunk capture ceiling; a single chunk is never split across ticks.
 */
final class RingAtlasCaptureBudget {
    static final int MAX_CHUNKS = 4;
    static final long NANOS = 2000000;

    static boolean allows(int captured, int requestLimit, long elapsedNanos) {
        return captured < Math.min(MAX_CHUNKS, requestLimit) && (captured == 0 || elapsedNanos < NANOS);
    }
}
