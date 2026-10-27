package dev.ringworld.server;

/**
 * Cleanup must catch up before deferred holders turn a short hitch into heap exhaustion.
 */
public record RingChunkMaintenanceBudget(int tasks, int saves, long nanos) {
    private static final RingChunkMaintenanceBudget NORMAL = new RingChunkMaintenanceBudget(256, 16, 2000000);
    private static final RingChunkMaintenanceBudget CATCH_UP = new RingChunkMaintenanceBudget(4096, 64, 8000000);
    private static final RingChunkMaintenanceBudget PRESSURE = new RingChunkMaintenanceBudget(16384, 128, 20000000);

    public static RingChunkMaintenanceBudget select(long backlog, long usedHeap, long maxHeap) {
        if (backlog >= 8192 || (maxHeap > 0 && usedHeap >= maxHeap - maxHeap / 10)) return PRESSURE;
        if (backlog > 2000 || (maxHeap > 0 && usedHeap >= maxHeap - maxHeap / 5)) return CATCH_UP;
        return NORMAL;
    }
}
