package dev.ringworld.world;

import dev.ringworld.world.atlas.RingTerrainAtlas;

/**
 * Local display choices. These IDs are not saved generation settings or network values.
 */
public enum RingLodQuality {
    LOW(8, 4096, 1024, 8), MEDIUM(2, 16384, 1024, 4), HIGH(1, 32768, 2048, 1);

    private final int sampleStep, columns, rows, meshStep;

    RingLodQuality(int sampleStep, int columns, int rows, int meshStep) {
        this.sampleStep = sampleStep;
        this.columns = columns;
        this.rows = rows;
        this.meshStep = meshStep;
    }

    public int sampleStep() {
        return sampleStep;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public int meshStep() {
        return meshStep;
    }

    /**
     * Retains captured colour/height anchors and averages water coverage over each footprint.
     */
    public RingTerrainAtlas displaySnapshot(RingTerrainAtlas source) {
        if (!source.isComplete() || sampleStep <= source.sampleStep()) return source.snapshot();
        var result = new RingTerrainAtlas(source.geometry(), source.dimension(), source.worldHash(), sampleStep);
        for (int row = 0; row < result.rows(); row++) {
            int sourceRow = Math.min(source.rows() - 1, (row * sampleStep + sampleStep / 2) / source.sampleStep());
            for (int col = 0; col < result.columns(); col++) {
                int sourceCol = Math.min(source.columns() - 1, (col * sampleStep + sampleStep / 2) / source.sampleStep());
                result.putCell(col, row, source.cellHeight(sourceCol, sourceRow), source.cellColor(sourceCol, sourceRow), source.cellBlockLight(sourceCol, sourceRow), source.cellSideColor(sourceCol, sourceRow), waterCoverage(source, col, row), source.cellLayer(sourceCol, sourceRow));
            }
        }
        return result;
    }

    private int waterCoverage(RingTerrainAtlas source, int column, int row) {
        int scale = sampleStep / source.sampleStep();
        int sum = 0, count = 0;
        for (int z = row * scale; z < Math.min(source.rows(), (row + 1) * scale); z++) {
            for (int x = column * scale; x < Math.min(source.columns(), (column + 1) * scale); x++) {
                sum += source.cellWaterCoverage(x, z);
                count++;
            }
        }
        if (sum == 0 || sum == count * 15) return sum == 0 ? 0 : 15;
        // Keep even a single wet or dry block distinguishable at Low.
        return Math.clamp((int) Math.round((double) sum / count), 1, 14);
    }
}
