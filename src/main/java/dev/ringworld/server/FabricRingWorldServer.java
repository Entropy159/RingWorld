package dev.ringworld.server;

import dev.ringworld.api.RingWorldApi;
import dev.ringworld.net.RingWorldNetworking;
import dev.ringworld.server.atlas.FabricTerrainAtlasPlatform;
import dev.ringworld.server.atlas.RingTerrainAtlasServer;
import dev.ringworld.server.atlas.pregen.RingAtlasPregenerationService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Fabric lifecycle adapter for the loader-neutral authoritative server core.
 */
public final class FabricRingWorldServer {
    public static void register() {
        FabricTerrainAtlasPlatform.register();
        ServerTickEvents.END_LEVEL_TICK.register(world -> {
            RingWorldServer.tickRingWorld(world);
            if (RingWorldApi.isRingWorld(world)) RingTerrainAtlasServer.tick(world);
        });
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk, generated) -> {
            if (!RingWorldApi.isRingWorld(world)) return;
            RingTerrainAtlasServer.captureLoadedChunk(world, chunk);
            RingWorldServer.onChunkLoaded(world, chunk);
        });
        ServerLevelEvents.LOAD.register((server, world) -> {
            RingWorldServer.attachWorldGeometry(world);
            RingAtlasPregenerationService.load(world);
        });
        ServerLevelEvents.UNLOAD.register((server, world) -> {
            RingTerrainAtlasServer.unload(world);
            RingWorldServer.onLevelUnloaded(world);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            RingWorldServer.onPlayerJoined(handler.player);
        });
        RingWorldNetworking.registerServer();
    }
}
