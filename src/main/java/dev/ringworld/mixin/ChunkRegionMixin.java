package dev.ringworld.mixin;

import dev.ringworld.api.RingWorldApi;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTickSchedulerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.ticks.WorldGenTickAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Canonicalizes positions committed by a seam-crossing worldgen region.
 */
@Mixin(WorldGenRegion.class)
public abstract class ChunkRegionMixin {
    @Shadow
    @Final
    private ServerLevel level;
    @Shadow
    @Final
    private ChunkAccess center;
    @Shadow
    @Final
    private WorldGenTickAccess<?> blockTicks;
    @Shadow
    @Final
    private WorldGenTickAccess<?> fluidTicks;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ringworld$attachGenerationSchedulers(CallbackInfo ci) {
        RingGeometry geometry = geometry();
        if (geometry == null) return;
        ((RingTickSchedulerAccess) blockTicks).ringworld$setGeometry(geometry);
        ((RingTickSchedulerAccess) fluidTicks).ringworld$setGeometry(geometry);
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StaticCache2D;map(Lnet/minecraft/util/StaticCache2D$MappingFunction;)Lnet/minecraft/util/StaticCache2D;"))
    private StaticCache2D<ChunkAccess> ringworld$mapPeriodicGenerationCache(StaticCache2D<GenerationChunkHolder> cache, StaticCache2D.MappingFunction<GenerationChunkHolder, ChunkAccess> mapping, ServerLevel world, StaticCache2D<GenerationChunkHolder> originalCache, ChunkStep step, ChunkAccess centralChunk) {
        if (!RingWorldApi.isRingWorld(world)) return cache.map(mapping);
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        return cache.map((holder, x, z) -> mapping.apply(holder, RingChunkCoordinates.nearestImageChunkX(x, centralChunk.getPos().x(), geometry), z));
    }

    @ModifyVariable(method = "getBlockEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/WorldGenRegion;getChunk(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/chunk/ChunkAccess;", shift = At.Shift.AFTER), argsOnly = true, name = "pos")
    private BlockPos ringworld$canonicalReadPosition(BlockPos pos) {
        return canonical(pos);
    }

    /**
     * Preserve vanilla's local write-radius validation, then canonicalize the
     * argument before it is committed to the periodic holder. This keeps
     * pending block-entity NBT coordinates consistent with chunk 99 when a
     * feature writes through the -1 alias, for example.
     */
    @ModifyVariable(method = "setBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/WorldGenRegion;getChunk(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/chunk/ChunkAccess;", shift = At.Shift.AFTER), argsOnly = true, name = "pos")
    private BlockPos ringworld$canonicalWritePosition(BlockPos pos) {
        return canonical(pos);
    }

    /**
     * Select the holder through its nearby alias while retaining canonical NBT coordinates.
     */
    @Redirect(method = "markPosForPostProcessing", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/WorldGenRegion;getChunk(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/chunk/ChunkAccess;"))
    private ChunkAccess ringworld$getPostProcessingChunkThroughLocalAlias(WorldGenRegion region, BlockPos canonicalPos) {
        RingGeometry geometry = geometry();
        if (geometry == null) return region.getChunk(canonicalPos);
        double referenceX = center.getPos().getMiddleBlockX();
        int aliasX = (int) Math.floor(geometry.nearestImageX(canonicalPos.getX(), referenceX));
        return region.getChunk(new BlockPos(aliasX, canonicalPos.getY(), canonicalPos.getZ()));
    }

    @ModifyVariable(method = "addFreshEntity", at = @At("HEAD"), argsOnly = true, name = "entity")
    private Entity ringworld$canonicalGeneratedEntity(Entity entity) {
        RingGeometry geometry = geometry();
        if (geometry != null) {
            entity.setPos(geometry.wrapX(entity.getX()), entity.getY(), entity.getZ());
        }
        return entity;
    }

    @Unique
    private BlockPos canonical(BlockPos pos) {
        RingGeometry geometry = geometry();
        if (geometry == null) return pos;
        int x = geometry.wrapBlockX(pos.getX());
        return x == pos.getX() ? pos : new BlockPos(x, pos.getY(), pos.getZ());
    }

    @Unique
    private RingGeometry geometry() {
        return RingWorldApi.isRingWorld(level) ? RingWorldServer.geometryFor(level) : null;
    }
}
