package dev.ringworld.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import dev.ringworld.RingWorldMod;
import dev.ringworld.api.RingWorldApi;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes structure locating respect the one canonical, periodic Overworld plane.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorLocateMixin {
    @Redirect(method = {"findNearestMapStructure", "getNearestGeneratedStructure(Ljava/util/Set;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/StructureManager;IIIZJLnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;)Lcom/mojang/datafixers/util/Pair;"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"))
    private double excludeOutsideRing(BlockPos instance, Vec3i vec3i, @Local(argsOnly = true, name = "level") ServerLevel level) {
        double distance = instance.distSqr(vec3i);
        if (RingWorldApi.isRingWorld(level)) {
            RingGeometry geometry = RingWorldServer.geometryFor(level);
            if (geometry.isOutsideWidth(vec3i.getZ())) {
                RingWorldMod.LOGGER.debug("Ignoring structure candidate {}, out of bounds", vec3i);
                return Double.MAX_VALUE;
            }
        }
        return distance;
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
