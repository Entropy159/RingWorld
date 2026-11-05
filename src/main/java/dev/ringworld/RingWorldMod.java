package dev.ringworld;

import dev.ringworld.api.RingCompatibilityContract;
import dev.ringworld.api.RingWorldApi;
import dev.ringworld.config.wallpattern.*;
import dev.ringworld.net.RingWorldNetworking;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.server.atlas.RingTerrainAtlasServer;
import dev.ringworld.server.atlas.pregen.RingAtlasPregenerationService;
import dev.ringworld.world.wall.RingWallPattern;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RingWorldMod implements ModInitializer {
    public static final String MOD_ID = "ringworld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        logLoadedConflicts();

        RingWorldNetworking.registerPayloads();
        RingWorldNetworking.registerServer();

        RingWallPattern.addPattern(new ClusteredWallPattern());
        RingWallPattern.addPattern(new EngineeredWallPattern());
        RingWallPattern.addPattern(new GradientWallPattern());
        RingWallPattern.addPattern(new HybridWallPattern());
        RingWallPattern.addPattern(new MasonryWallPattern());
        RingWallPattern.addPattern(new PanelWallPattern());
        RingWallPattern.addPattern(new StrataWallPattern());

        ServerTickEvents.END_LEVEL_TICK.register(world -> {
            RingWorldServer.tickRingWorld(world);
            if (RingWorldApi.isRingWorld(world)) RingTerrainAtlasServer.tick(world);
        });
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk, generated) -> {
            if (!RingWorldApi.isRingWorld(world)) return;
            RingTerrainAtlasServer.captureLoadedChunk(world, chunk);
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
        
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> RingTerrainAtlasServer.registerCommands(dispatcher));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void logLoadedConflicts() {
        var loadedIds = FabricLoader.getInstance().getAllMods().stream().map(container -> container.getMetadata().getId()).toList();
        for (RingCompatibilityContract.Conflict conflict : RingCompatibilityContract.findLoadedConflicts(loadedIds)) {
            RingWorldMod.LOGGER.error("Unsupported RingWorld combination detected: {} ({}): {}. " + "Remove it before reporting RingWorld rendering or topology defects.", conflict.displayName(), conflict.modId(), conflict.reason());
        }
    }
}
