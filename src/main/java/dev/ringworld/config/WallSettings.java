package dev.ringworld.config;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Function;

public record WallSettings(int height, int thickness, Palette palette, Pattern pattern, int decay) {
    private static final int MIN_THICKNESS = 1;
    private static final int MAX_THICKNESS = 32;
    private static final int MIN_HEIGHT = 32;

    public static final WallSettings DEFAULT = new WallSettings(160, 5, Palette.OBSIDIAN, Pattern.ENGINEERED, 5);

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

    public enum Palette {
        WEATHERED(roll -> roll < 55 ? Blocks.COBBLESTONE.defaultBlockState() : roll < 80 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : roll < 95 ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState()),
        ANCIENT(roll -> roll < 50 ? Blocks.STONE_BRICKS.defaultBlockState() : roll < 72 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : roll < 94 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState()),
        NATURAL(roll -> roll < 45 ? Blocks.STONE.defaultBlockState() : roll < 70 ? Blocks.TUFF.defaultBlockState() : roll < 90 ? Blocks.ANDESITE.defaultBlockState() : roll < 98 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState()),
        ALLOY(roll -> roll < 40 ? Blocks.SMOOTH_STONE.defaultBlockState() : roll < 70 ? Blocks.POLISHED_DIORITE.defaultBlockState() : roll < 95 ? Blocks.QUARTZ_BLOCK.defaultBlockState() : Blocks.PRISMARINE_BRICKS.defaultBlockState()),
        INDUSTRIAL(roll -> roll < 40 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : roll < 68 ? Blocks.DEEPSLATE_TILES.defaultBlockState() : roll < 86 ? Blocks.POLISHED_BASALT.defaultBlockState() : roll < 97 ? Blocks.TUFF_BRICKS.defaultBlockState() : Blocks.RAW_COPPER_BLOCK.defaultBlockState()),
        OVERGROWN(roll -> roll < 38 ? Blocks.STONE_BRICKS.defaultBlockState() : roll < 60 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : roll < 78 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : roll < 92 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState()),
        MONOLITH(roll -> roll < 55 ? Blocks.SMOOTH_STONE.defaultBlockState() : roll < 82 ? Blocks.CALCITE.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState()),
        NETHER(roll -> roll < 42 ? Blocks.NETHER_BRICKS.defaultBlockState() : roll < 60 ? Blocks.RED_NETHER_BRICKS.defaultBlockState() : roll < 78 ? Blocks.BLACKSTONE.defaultBlockState() : roll < 96 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState()),
        OBSIDIAN(roll -> roll < 55 ? Blocks.OBSIDIAN.defaultBlockState() : roll < 72 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : roll < 90 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : roll < 97 ? Blocks.GILDED_BLACKSTONE.defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState()),
        WOOD(roll -> roll < 30 ? Blocks.OAK_LOG.defaultBlockState() : roll < 54 ? Blocks.SPRUCE_LOG.defaultBlockState() : roll < 72 ? Blocks.DARK_OAK_LOG.defaultBlockState() : roll < 90 ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());

        private final Function<Integer, BlockState> blockFunction;

        Palette(Function<Integer, BlockState> stateFunction) {
            blockFunction = stateFunction;
        }

        public BlockState get(int roll) {
            return blockFunction.apply(roll);
        }
    }

    public enum Pattern {
        CLUSTERED, MASONRY, STRATA, PANELS, GRADIENT, HYBRID, ENGINEERED;
    }
}
