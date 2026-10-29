package dev.ringworld.mixin;

import dev.ringworld.api.RingWorldApi;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingPlayerMovementAccess;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Accepts a client's continuous presentation coordinate while keeping the
 * authoritative server entity in the single canonical circumference plane.
 * The fold happens after vanilla validates the small local step and does not
 * send a corrective teleport back to the client.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin implements RingPlayerMovementAccess {
    @Shadow
    public ServerPlayer player;
    @Shadow
    private double firstGoodX;
    @Shadow
    private double lastGoodX;
    @Shadow
    private double vehicleFirstGoodX;
    @Shadow
    private double vehicleLastGoodX;

    @Override
    public void ringworld$resetPlayerMovementBaselines() {
        firstGoodX = player.getX();
        lastGoodX = player.getX();
    }

    // Both ordinary movement and teleport acknowledgements reach this method
    // in 26.3. Project before vanilla measures either against its baselines.
    @ModifyVariable(method = "handlePlayerPositionChange", at = @At("HEAD"), argsOnly = true, name = "requestedX")
    private double ringworld$projectPlayerMovement(double presentationX) {
        ServerLevel world = player.level();
        if (!RingWorldApi.isRingWorld(world)) return presentationX;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        double nearestX = geometry.nearestImageX(presentationX, player.getX());
        double canonicalTargetX = geometry.wrapX(nearestX);

        // Server-owned pose changes such as entering a bed can choose the
        // adjacent canonical image without passing through this packet path.
        // Bring vanilla's anti-cheat baselines back to the image nearest the
        // authoritative pose before it computes a flat-space displacement.
        double alignedFirstGoodX = geometry.nearestImageX(firstGoodX, player.getX());
        double baselineShift = alignedFirstGoodX - firstGoodX;
        if (baselineShift != 0.0) {
            firstGoodX += baselineShift;
            lastGoodX += baselineShift;
        }

        // Validate a seam crossing entirely within the destination chart.
        // Letting vanilla move the bounding box from C-epsilon to C makes its
        // collision reconciliation inspect a box spanning two periodic block
        // charts, which can reject the otherwise ordinary final step. Shift
        // the source pose and anti-cheat baselines first, then give vanilla a
        // local epsilon-sized move ending directly in canonical storage.
        if (canonicalTargetX != nearestX) {
            double localSourceX = geometry.nearestImageX(player.getX(), canonicalTargetX);
            double sourceShift = localSourceX - player.getX();
            if (sourceShift != 0.0) {
                player.setPos(localSourceX, player.getY(), player.getZ());
                firstGoodX += sourceShift;
                lastGoodX += sourceShift;
            }
        }

        return canonicalTargetX;
    }

    @Inject(method = "handlePlayerPositionChange", at = @At("TAIL"))
    private void ringworld$foldPlayerAfterMovement(CallbackInfo ci) {
        ServerLevel world = player.level();
        if (!RingWorldApi.isRingWorld(world)) return;
        double shift = RingWorldServer.canonicalizeEntityPosition(player, RingWorldServer.geometryFor(world));
        if (shift == 0.0) return;
        // Vanilla's anti-cheat baselines must move to the same coordinate
        // chart or the next perfectly ordinary packet looks C blocks long.
        firstGoodX += shift;
        lastGoodX += shift;
    }

    @ModifyVariable(method = "handleMoveVehicle", at = @At("HEAD"), argsOnly = true, name = "packet")
    private ServerboundMoveVehiclePacket ringworld$projectVehicleMovement(ServerboundMoveVehiclePacket packet) {
        ServerLevel world = player.level();
        Entity vehicle = player.getRootVehicle();
        if (!RingWorldApi.isRingWorld(world) || vehicle == player) return packet;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        Vec3 position = packet.movingTo().position();
        double nearestX = geometry.nearestImageX(position.x, vehicle.getX());
        if (nearestX == position.x) return packet;
        return new ServerboundMoveVehiclePacket(PositionAndRotation.of(new Vec3(nearestX, position.y, position.z), packet.movingTo().yRot(), packet.movingTo().xRot()), packet.onGround());
    }

    @Inject(method = "handleMoveVehicle", at = @At("TAIL"))
    private void ringworld$foldVehicleAfterMovement(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        ServerLevel world = player.level();
        Entity vehicle = player.getRootVehicle();
        if (!RingWorldApi.isRingWorld(world) || vehicle == player) return;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        double vehicleShift = RingWorldServer.canonicalizeEntityPosition(vehicle, geometry);
        if (vehicleShift != 0.0) {
            vehicleFirstGoodX += vehicleShift;
            vehicleLastGoodX += vehicleShift;
        }
        // Passenger poses were calculated before the vehicle folded.
        vehicle.getSelfAndPassengers().skip(1).forEach(passenger -> {
            double passengerShift = RingWorldServer.canonicalizeEntityPosition(passenger, geometry);
            if (passenger == player && passengerShift != 0.0) {
                firstGoodX += passengerShift;
                lastGoodX += passengerShift;
            }
        });
    }
}
