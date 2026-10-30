package dev.ringworld.world;

import dev.ringworld.config.WallSettings;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by the vanilla noise generator mixin once it belongs to a ring Overworld.
 */
public interface RingWorldGeneratorAccess {
    void ringworld$setGeometry(@Nullable RingGeometry geometry);

    RingGeometry ringworld$getGeometry();

    int ringworld$getTerrainNoiseMapping();

    void ringworld$setWallHeight(int wallHeightBlocks);

    int ringworld$getWallHeight();

    void ringworld$setWallStyle(WallSettings wallStyle);

    WallSettings ringworld$getWallStyle();

    void ringworld$setGuaranteeStronghold(boolean guaranteeStronghold);

    boolean ringworld$guaranteesStronghold();

    Climate.Sampler ringworld$getPeriodicClimateSampler(RandomState randomState);
}
