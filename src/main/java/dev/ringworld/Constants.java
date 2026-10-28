package dev.ringworld;

import dev.ringworld.world.RingLodQuality;
import dev.ringworld.world.RingSkyProfile;
import dev.ringworld.world.RingWorldCreationUiModel;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class Constants {
    public static final int DEFAULT_MIN_Y = -64;
    public static final int DEFAULT_MAX_Y = 320;
    public static final int MIN_WALL_HEIGHT_BLOCKS = 32;
    public static final int MIN_RADIAL_CLEARANCE_BLOCKS = 64;
    public static final int CLOUD_CLEARANCE_BLOCKS = 48; //Default: 8
    public static final int MIN_PLAYABLE_INTERIOR_BLOCKS = 64;
    public static final double NORMAL_WALKING_SPEED_BLOCKS_PER_SECOND = 4.317;
    public static final int MAX_AXIS_BLOCKS = 1048576;
    public static final long WARN_CANONICAL_CHUNKS = 500000L;
    public static final long WARN_ATLAS_CELLS = 4000000L;
    public static final long MAX_ATLAS_CELLS = 16777216L * 16; //Default: 16777216
    public static final long WARN_PREGENERATION_SECONDS = 30L * 60L;
    public static final long WARN_GENERATED_WORLD_BYTES = 512L * 1024L * 1024L;
    public static final RingLodQuality DEFAULT_LOD_QUALITY = RingLodQuality.HIGH; //Default: MEDIUM
    public static final int DEFAULT_WIDTH = 256;
    public static final int DEFAULT_CIRCUMFERENCE = 16384;
    public static final int DEFAULT_WALL_HEIGHT = 160;
    public static final int MIN_WIDTH = 128;
    public static final int MIN_CIRCUMFERENCE = 2048;
    public static final RingSkyProfile DEFAULT_SKY_PROFILE = new RingSkyProfile(RingSkyProfile.Backdrop.ATMOSPHERE, RingSkyProfile.LightSource.SMALL, RingSkyProfile.FORMAT_VERSION);
    public static final float SUN_ANGLE_OFFSET_Y = 20;

    public static final TagKey<DimensionType> RING_DIMENSION_TAG = TagKey.create(Registries.DIMENSION_TYPE, RingWorldMod.id("ring"));

    public static Collection<RingWorldCreationUiModel.Preset> defaultPresets() {
        List<RingWorldCreationUiModel.Preset> presets = new ArrayList<>();
        presets.add(new RingWorldCreationUiModel.Preset("Small", 2048, 128, DEFAULT_WALL_HEIGHT, "Strong curvature; monuments unavailable.", "Small is experimental: portal may need mining."));
        presets.add(new RingWorldCreationUiModel.Preset("Medium", DEFAULT_CIRCUMFERENCE, DEFAULT_WIDTH, DEFAULT_WALL_HEIGHT, "Balanced default.", "Medium is the recommended starting size."));
        presets.add(new RingWorldCreationUiModel.Preset("Large", 32768, 512, DEFAULT_WALL_HEIGHT, "Long lap; slower generation.", "Large may take longer to generate."));
        presets.add(new RingWorldCreationUiModel.Preset("XL", 65536, 1024, DEFAULT_WALL_HEIGHT, "Extra large world for more exploration.", "For the brave with good PCs."));
        presets.add(new RingWorldCreationUiModel.Preset("XXL", 131072, 2048, DEFAULT_WALL_HEIGHT, "Maximum ring size, use with caution.", "NASA PCs only, this is as big as it gets."));
        return presets;
    }
}
