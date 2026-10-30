package dev.ringworld;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.dimension.DimensionType;

public final class Constants {
    public static final int DEFAULT_MIN_Y = -64;
    public static final int CLOUD_CLEARANCE_BLOCKS = 48; //Default: 8
    public static final float CLOUD_FADE_BLOCKS = 16;

    public static final TagKey<DimensionType> RING_DIMENSION_TAG = TagKey.create(Registries.DIMENSION_TYPE, RingWorldMod.id("ring"));
}