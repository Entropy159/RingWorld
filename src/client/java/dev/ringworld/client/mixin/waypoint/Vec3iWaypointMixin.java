package dev.ringworld.client.mixin.waypoint;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ringworld.client.ClientRingState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.waypoints.TrackedWaypoint$Vec3iWaypoint")
public abstract class Vec3iWaypointMixin {
    @ModifyReturnValue(method = "position", at = @At("RETURN"))
    private Vec3 ringPos(Vec3 original) {
        if (original == null) {
            return null;
        }
        var geometry = ClientRingState.geometry();
        var camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        if (geometry != null) {
            return geometry.toCameraLocal(original, camera).add(camera);
        }
        return original;
    }

    @Redirect(method = "distanceSquared", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double realDistance(Entity instance, Vec3 pos) {
        var geometry = ClientRingState.geometry();
        if (geometry != null) {
            double dx = geometry.shortestCircumferenceDelta(instance.getX(), pos.x);
            double dy = instance.getY() - pos.y;
            double dz = instance.getZ() - pos.z;
            return dx * dx + dy * dy + dz * dz;
        }
        return instance.distanceToSqr(pos);
    }
}
