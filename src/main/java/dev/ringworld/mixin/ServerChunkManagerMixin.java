package dev.ringworld.mixin;

import dev.ringworld.api.RingWorldApi;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingChunkLevelContext;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes every server chunk acquisition use the canonical circumference chunk.
 * This is deliberately below the entity layer so chunk tickets, chunk-status
 * dependencies, and worldgen neighbour reads use the same ring topology.
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkManagerMixin {
    @Shadow
    public abstract Level getLevel();

    @ModifyVariable(method = {"getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", "getChunkFutureMainThread", "getChunkNow"}, at = @At("HEAD"), argsOnly = true, name = "x")
    private int ringworld$canonicalChunkX(int chunkX) {
        return RingWorldApi.wrapChunkX(chunkX, getLevel());
    }

    /**
     * Canonicalize world-facing holder lookups without touching generation's internal holder graph.
     */
    @ModifyVariable(method = "getVisibleChunkIfPresent", at = @At("HEAD"), argsOnly = true, name = "key")
    private long ringworld$canonicalHolderLookup(long packedPos) {
        Level world = getLevel();
        if (!(world instanceof ServerLevel serverWorld) || !RingWorldApi.isRingWorld(serverWorld)) {
            return packedPos;
        }
        ChunkPos pos = ChunkPos.unpack(packedPos);
        RingGeometry geometry = RingWorldServer.geometryFor(serverWorld);
        return ChunkPos.pack(RingChunkCoordinates.wrapChunkX(pos.x(), geometry), pos.z());
    }

    /**
     * Every external ticket source must enter the finite canonical graph.
     */
    @ModifyVariable(method = {"addTicket(Lnet/minecraft/server/level/Ticket;Lnet/minecraft/world/level/ChunkPos;)V", "addTicketAndLoadWithRadius", "addTicketWithRadius(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;I)V", "removeTicketWithRadius", "updateChunkForced"}, at = @At("HEAD"), argsOnly = true, name = "pos")
    private ChunkPos ringworld$canonicalTicketPosition(ChunkPos pos) {
        Level world = getLevel();
        if (!(world instanceof ServerLevel serverWorld) || !RingWorldApi.isRingWorld(serverWorld)) {
            return pos;
        }
        RingGeometry geometry = RingWorldServer.geometryFor(serverWorld);
        return new ChunkPos(RingChunkCoordinates.wrapChunkX(pos.x(), geometry), pos.z());
    }

    @Redirect(method = "runDistanceManagerUpdates", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DistanceManager;runAllUpdates(Lnet/minecraft/server/level/ChunkMap;)Z"))
    private boolean ringworld$updatePeriodicChunkGraph(DistanceManager manager, ChunkMap loadingManager) {
        Level world = getLevel();
        if (!(world instanceof ServerLevel serverWorld) || !RingWorldApi.isRingWorld(serverWorld)) {
            return manager.runAllUpdates(loadingManager);
        }
        RingGeometry geometry = RingWorldServer.geometryFor(serverWorld);
        return RingChunkLevelContext.run(geometry, () -> manager.runAllUpdates(loadingManager));
    }
}
