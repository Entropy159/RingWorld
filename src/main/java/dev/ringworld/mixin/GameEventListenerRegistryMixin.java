package dev.ringworld.mixin;

import dev.ringworld.api.RingWorldApi;
import dev.ringworld.server.RingWorldServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EuclideanGameEventListenerRegistry.class)
public abstract class GameEventListenerRegistryMixin {
    @Redirect(method = "getPostableListenerPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"))
    private static double ringworld$listenerDistance(BlockPos listenerPos, Vec3i sourcePos, ServerLevel level, Vec3 source, GameEventListener listener) {
        if (!RingWorldApi.isRingWorld(level)) return listenerPos.distSqr(sourcePos);
        var geometry = RingWorldServer.geometryFor(level);
        BlockPos position = BlockPos.containing(source);
        BlockPos nearby = new BlockPos((int) geometry.nearestImageX(position.getX(), listenerPos.getX()), position.getY(), position.getZ());
        return listenerPos.distSqr(nearby);
    }
}
