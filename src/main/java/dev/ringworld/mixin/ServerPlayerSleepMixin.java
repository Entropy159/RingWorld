package dev.ringworld.mixin;

import dev.ringworld.api.RingWorldApi;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingPlayerMovementAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes vanilla's private bed reach check use the nearest periodic image.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSleepMixin {
    @Inject(method = "isReachableBedBlock", at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicBedReach(BlockPos bedPos, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!RingWorldApi.isRingWorld(player.level())) return;
        RingGeometry geometry = RingWorldServer.geometryFor(player.level());
        Vec3 bedCenter = Vec3.atBottomCenterOf(bedPos);
        cir.setReturnValue(geometry.isWithinPeriodicBox(player.getX(), player.getY(), player.getZ(), bedCenter.x(), bedCenter.y(), bedCenter.z(), 3.0, 2.0, 3.0));
    }

    @Inject(method = "startSleeping", at = @At("TAIL"))
    private void ringworld$alignMovementAfterSleeping(BlockPos bedPos, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (cir.getReturnValue() && RingWorldApi.isRingWorld(player.level()) && player.connection instanceof RingPlayerMovementAccess access) {
            access.ringworld$resetPlayerMovementBaselines();
        }
    }
}
