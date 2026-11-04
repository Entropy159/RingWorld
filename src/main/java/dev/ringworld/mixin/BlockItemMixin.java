package dev.ringworld.mixin;

import dev.ringworld.api.RingWorldApi;
import dev.ringworld.config.RingWorldConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reject player placement before any block, item, sound or inventory mutation.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void ringworld$placementPolicy(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (context.getPlayer() != null && context.getLevel() instanceof ServerLevel world && RingWorldApi.isRingWorld(world)) {
            var config = RingWorldConfig.get(world);
            if (config != null && !config.allowBuildingOutside() && config.geometry().isOutsideWidth(context.getClickedPos().relative(context.getHorizontalDirection()).getZ())) {
                cir.setReturnValue(false);
            }
        }
    }
}
