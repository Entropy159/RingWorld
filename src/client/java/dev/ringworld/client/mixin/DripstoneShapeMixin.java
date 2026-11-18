package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SpeleothemBlock.class)
public abstract class DripstoneShapeMixin {
    @ModifyVariable(method = "getShape", at = @At("HEAD"), argsOnly = true, name = "pos")
    private BlockPos ringworld$shapePosition(BlockPos position, BlockState state, BlockGetter level, BlockPos original, CollisionContext context) {
        return level instanceof ClientLevel ? ClientRingState.dripstoneOffsetPosition(state, position) : position;
    }
}
