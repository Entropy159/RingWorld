package dev.ringworld.config;

import dev.ringworld.world.wall.RingEngineeredWallPattern;
import dev.ringworld.world.wall.RingWallPattern;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.function.Function;

public record WallSettings(int height, int thickness, LinkedHashMap<BlockState, Integer> palette, Pattern pattern,
                           int decay) {
    public static final LinkedHashMap<BlockState, Integer> DEFAULT_PALETTE = new LinkedHashMap<>() {{
        put(Blocks.OBSIDIAN.defaultBlockState(), 50);
        put(Blocks.CRYING_OBSIDIAN.defaultBlockState(), 15);
        put(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 15);
        put(Blocks.GILDED_BLACKSTONE.defaultBlockState(), 10);
        put(Blocks.AMETHYST_BLOCK.defaultBlockState(), 2);
    }};
    public static final WallSettings DEFAULT = new WallSettings(160, 5, DEFAULT_PALETTE, Pattern.ENGINEERED, 5);

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
        for (var entry : palette.entrySet()) {
            int chance = entry.getValue();
            if (roll <= chance) {
                return entry.getKey();
            }
            roll -= chance;
        }
        return palette.lastEntry().getKey();
    }

    public enum Pattern {
        CLUSTERED(info -> RingWallPattern.blend(info.coarse(), info.fine(), 72, info.totalRolls())),
        MASONRY(info -> {
            int course = Math.floorDiv(info.y(), 2);
            int brickWidth = 3 + RingWallPattern.materialNoise(info.worldSeed(), RingWallPattern.MATERIAL_SALT, course, info.depth(), 0, info.style().palette(), info.totalRolls()) % 4;
            int offset = RingWallPattern.materialNoise(info.worldSeed(), RingWallPattern.MATERIAL_COARSE_SALT, course, info.depth(), 1, info.style().palette(), info.totalRolls()) % brickWidth;
            int brick = Math.floorDiv(info.canonicalX() + offset, brickWidth);
            int body = RingWallPattern.materialNoise(info.worldSeed(), RingWallPattern.MATERIAL_SALT, brick, course, info.depth(), info.style().palette(), info.totalRolls());
            return RingWallPattern.blend(body, info.fine(), 76, info.totalRolls());
        }),
        STRATA(info -> {
            int section = Math.floorDiv(info.canonicalX(), 19);
            int wave = RingWallPattern.materialNoise(info.worldSeed(), RingWallPattern.MATERIAL_COARSE_SALT, section, info.depth(), 2, info.style().palette(), info.totalRolls()) % 9 - 4;
            int bandHeight = 3 + RingWallPattern.materialNoise(info.worldSeed(), RingWallPattern.MATERIAL_SALT, section, Math.floorDiv(info.y(), 13), info.depth(), info.style().palette(), info.totalRolls()) % 7;
            int band = Math.floorDiv(info.y() + wave, bandHeight);
            int body = RingWallPattern.materialNoise(info.worldSeed(), RingWallPattern.MATERIAL_SALT, Math.floorDiv(info.canonicalX(), 11), band, info.depth(), info.style().palette(), info.totalRolls());
            return RingWallPattern.blend(body, info.fine(), 68, info.totalRolls());
        }),
        PANELS(info -> RingWallPattern.panelRoll(info.style(), info.canonicalX(), info.y(), info.depth(), info.worldSeed(), info.fine(), info.totalRolls())),
        GRADIENT(info -> {
            int vertical = Math.clamp((info.y() + 64L) * info.totalRolls() / 224, 0, info.totalRolls());
            int grain = RingWallPattern.blend(info.coarse(), info.fine(), 46, info.totalRolls());
            return Math.clamp((vertical * 28L + grain * 72L) / info.totalRolls(), 0, info.totalRolls());
        }),
        HYBRID(info -> {
            int clustered = RingWallPattern.blend(info.coarse(), info.fine(), 66, info.totalRolls());
            int panel = RingWallPattern.panelRoll(info.style(), info.canonicalX(), info.y(), info.depth(), info.worldSeed(), info.fine(), info.totalRolls());
            int selector = RingWallPattern.materialNoise(info.worldSeed(), RingWallPattern.MATERIAL_SALT, Math.floorDiv(info.canonicalX(), 23), Math.floorDiv(info.y(), 17), info.depth(), info.style().palette(), info.totalRolls());
            return selector < 28 ? panel : RingWallPattern.blend(clustered, panel, 82, info.totalRolls());
        }),
        ENGINEERED(info -> RingEngineeredWallPattern.materialRoll(info.style(), info.x(), info.y(), info.depth(), info.circumference(), info.worldSeed(), info.totalRolls()));

        private final Function<RingWallPattern.Info, Integer> materialFunction;

        Pattern(Function<RingWallPattern.Info, Integer> function) {
            materialFunction = function;
        }

        public int materialRoll(RingWallPattern.Info info) {
            return materialFunction.apply(info);
        }
    }
}
