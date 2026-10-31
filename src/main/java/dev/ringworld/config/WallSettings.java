package dev.ringworld.config;

import dev.ringworld.config.wallpattern.EngineeredWallPattern;
import dev.ringworld.world.wall.RingWallPattern;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;

public record WallSettings(int height, int thickness, LinkedHashMap<BlockState, Integer> palette,
                           RingWallPattern pattern,
                           int decay) {
    public static final LinkedHashMap<BlockState, Integer> DEFAULT_PALETTE = new LinkedHashMap<>() {{
        put(Blocks.OBSIDIAN.defaultBlockState(), 50);
        put(Blocks.CRYING_OBSIDIAN.defaultBlockState(), 15);
        put(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 15);
        put(Blocks.GILDED_BLACKSTONE.defaultBlockState(), 10);
        put(Blocks.AMETHYST_BLOCK.defaultBlockState(), 2);
    }};
    public static final WallSettings DEFAULT = new WallSettings(160, 5, DEFAULT_PALETTE, new EngineeredWallPattern(), 5);

    private static final int MIN_THICKNESS = 1;
    private static final int MAX_THICKNESS = 32;
    private static final int MIN_HEIGHT = 32;

    public WallSettings {
        if (thickness < MIN_THICKNESS) {
            throw new IllegalArgumentException("Wall thickness must be more than " + MIN_THICKNESS);
        }
        if (thickness > MAX_THICKNESS) {
            throw new IllegalArgumentException("Wall thickness must be less than " + MAX_THICKNESS);
        }
        if (decay < 0 || decay > 100) {
            throw new IllegalArgumentException("Wall decay must be a valid percentage (0-100)");
        }
        if (height < MIN_HEIGHT) {
            throw new IllegalArgumentException("Wall height must be at least " + MIN_HEIGHT);
        }
    }

    public int getTotalRolls() {
        return palette.values().stream().reduce(0, Integer::sum);
    }

    public BlockState getFromRoll(int roll) {
        int currentRoll = roll;
        for (var entry : palette.entrySet()) {
            int chance = entry.getValue();
            if (currentRoll < chance) {
                return entry.getKey();
            }
            currentRoll -= chance;
        }
        return palette.lastEntry().getKey();
    }
}
