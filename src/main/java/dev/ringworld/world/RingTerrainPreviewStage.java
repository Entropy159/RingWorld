package dev.ringworld.world;

/**
 * Ordered quality stages for the disposable seed-derived terrain preview.
 */
public enum RingTerrainPreviewStage {
    CURRENT("current", "Current", 512, 16, 128, 8), HIGH("high", "High", 1024, 32, 256, 16), VERY_HIGH("very-high", "Very high", 2048, 32, 512, 16), ULTRA("ultra", "Ultra", 4096, 64, 1024, 32);

    private final String logLabel;
    private final String displayLabel;
    private final int colorColumns;
    private final int colorRows;
    private final int terrainColumns;
    private final int terrainRows;

    RingTerrainPreviewStage(String logLabel, String displayLabel, int colorColumns, int colorRows, int terrainColumns, int terrainRows) {
        this.logLabel = logLabel;
        this.displayLabel = displayLabel;
        this.colorColumns = colorColumns;
        this.colorRows = colorRows;
        this.terrainColumns = terrainColumns;
        this.terrainRows = terrainRows;
    }

    public String logLabel() {
        return logLabel;
    }

    public String displayLabel() {
        return displayLabel;
    }

    public int colorColumns() {
        return colorColumns;
    }

    public int colorRows() {
        return colorRows;
    }

    public int terrainColumns() {
        return terrainColumns;
    }

    public int terrainRows() {
        return terrainRows;
    }
}
