package dev.ringworld.mixin;

import dev.ringworld.world.RingCoordinateDensityFunction;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Tags coordinate-consuming density leaves without exposing protected vanilla types.
 */
@Mixin(targets = {
        "net.minecraft.world.level.levelgen.densityfunction.generator.NoiseFunction",
        "net.minecraft.world.level.levelgen.densityfunction.generator.ShiftNoiseFunction$Shift",
        "net.minecraft.world.level.levelgen.densityfunction.generator.ShiftNoiseFunction$ShiftA",
        "net.minecraft.world.level.levelgen.densityfunction.generator.ShiftNoiseFunction$ShiftB",
        "net.minecraft.world.level.levelgen.densityfunction.generator.EndIslandFunction"
})
public abstract class DensityCoordinateConsumerMixin implements RingCoordinateDensityFunction {
}
