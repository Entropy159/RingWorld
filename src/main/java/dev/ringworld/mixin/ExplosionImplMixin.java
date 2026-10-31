package dev.ringworld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ringworld.api.RingWorldApi;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Uses the nearest ring image for explosion exposure rays and impulse direction.
 */
@Mixin(ServerExplosion.class)
public abstract class ExplosionImplMixin {
    @Shadow
    @Final
    private ServerLevel level;
    @Shadow
    @Final
    private Vec3 center;

    @WrapOperation(method = "getSeenPercent", at = @At(value = "NEW", target = "(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/level/ClipContext;"))
    private static ClipContext ringworld$periodicExposureRay(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid, Entity entity, Operation<ClipContext> original) {
        if (entity.level() instanceof ServerLevel serverWorld && RingWorldApi.isRingWorld(serverWorld)) {
            RingGeometry geometry = RingWorldServer.geometryFor(serverWorld);
            from = new Vec3(geometry.nearestImageX(to.x, to.x), to.y, to.z);
        }
        return original.call(from, to, block, fluid, entity);
    }

    // NeoForge patches vanilla's no-argument method into a wrapper and
    // moves the entity loop into hurtEntities(List<BlockPos>). Match
    // both layouts while requiring the eye-position call in whichever
    // implementation actually owns the loop.
    @Redirect(method = "hurtEntities()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 ringworld$periodicKnockbackOrigin(Entity entity) {
        Vec3 entityEye = entity.getEyePosition();
        if (!RingWorldApi.isRingWorld(level)) return entityEye;
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        return new Vec3(geometry.nearestImageX(entityEye.x, center.x), entityEye.y, entityEye.z);
    }
}
