package dev.ringworld.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import dev.ringworld.RingWorldMod;
import dev.ringworld.api.RingWorldApi;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes structure locating respect the one canonical, periodic Overworld plane.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorLocateMixin {
    @Redirect(method = {"findNearestMapStructure", "getNearestGeneratedStructure(Ljava/util/Set;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/StructureManager;IIIZJLnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;)Lcom/mojang/datafixers/util/Pair;"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"))
    private double excludeOutsideRing(BlockPos instance, Vec3i vec3i, @Local(argsOnly = true, name = "level") ServerLevel level) {
        if (!RingWorldApi.isRingWorld(level)) {
            return instance.distSqr(vec3i);
        }
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        if (geometry.isOutsideWidth(vec3i.getZ())) {
            RingWorldMod.LOGGER.debug("Ignoring structure candidate {}, out of bounds", vec3i);
            return Double.MAX_VALUE;
        }
        BlockPos wrappedStart = new BlockPos(geometry.wrapBlockX(instance.getX()), instance.getY(), instance.getZ());
        BlockPos wrappedEnd = new BlockPos(geometry.wrapBlockX(vec3i.getX()), vec3i.getY(), vec3i.getZ());
        return wrappedStart.distSqr(wrappedEnd);
    }

    @ModifyVariable(method = "getNearestGeneratedStructure(Ljava/util/Set;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/StructureManager;IIIZJLnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;)Lcom/mojang/datafixers/util/Pair;", at = @At("STORE"), name = "chunkTarget")
    private static ChunkPos wrapPos(ChunkPos original, @Local(argsOnly = true, name = "level") LevelReader level) {
        if (!(level instanceof ServerLevel world) || !RingWorldApi.isRingWorld(world)) {
            return original;
        }
        var geometry = RingWorldServer.geometryFor(world);
        return new ChunkPos(RingChunkCoordinates.wrapChunkX(original.x(), geometry), original.z());
    }

    @Inject(method = "findNearestMapStructure", at = @At("RETURN"), cancellable = true)
    private void ringworld$nearestPeriodicStructureImage(ServerLevel world, HolderSet<Structure> structures, BlockPos origin, int searchRadius, boolean skipKnownStructures, CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
        if (!RingWorldApi.isRingWorld(world)) return;

        RingGeometry geometry = RingWorldServer.geometryFor(world);
        Pair<BlockPos, Holder<Structure>> result = cir.getReturnValue();

        if (result == null) return;
        BlockPos canonical = result.getFirst();
        int imageX = (int) Math.round(geometry.nearestImageX(canonical.getX(), origin.getX()));
        if (imageX != canonical.getX()) {
            result = Pair.of(new BlockPos(imageX, canonical.getY(), canonical.getZ()), result.getSecond());
        }
        cir.setReturnValue(result);
    }
}
