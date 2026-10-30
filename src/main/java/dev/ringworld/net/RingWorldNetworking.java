package dev.ringworld.net;

import dev.ringworld.config.RingWorldConfig;
import dev.ringworld.server.atlas.RingTerrainAtlasServer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Performs the mandatory client-mod handshake and ships immutable settings.
 */
public final class RingWorldNetworking {
    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(RingSettingsPayload.ID, RingSettingsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingBuildingSettingsPayload.ID, RingBuildingSettingsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingTerrainAtlasMetadataPayload.ID, RingTerrainAtlasMetadataPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingTerrainAtlasTilePayload.ID, RingTerrainAtlasTilePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingTerrainAtlasRevisionPayload.ID, RingTerrainAtlasRevisionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingTerrainPreviewPayload.ID, RingTerrainPreviewPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RingTerrainAtlasRequestPayload.ID, RingTerrainAtlasRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RingAtlasPregenerationStatusRequestPayload.ID, RingAtlasPregenerationStatusRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RingAtlasPregenerationControlPayload.ID, RingAtlasPregenerationControlPayload.CODEC);
    }

    public static void registerServer() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sendSettings(handler, handler.getPlayer().level());
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
            sendSettings(player.connection, to);
        });
        ServerPlayNetworking.registerGlobalReceiver(RingTerrainAtlasRequestPayload.ID, (payload, context) -> context.server().execute(() -> {
            RingTerrainAtlasServer.requestTiles(context.player(), payload.worldHash(), payload.revision(), payload.cacheComplete());
        }));
        ServerPlayNetworking.registerGlobalReceiver(RingAtlasPregenerationStatusRequestPayload.ID, (payload, context) -> context.server().execute(() -> {
            RingTerrainAtlasServer.requestPregenerationStatus(context.player(), payload.worldHash());
        }));
        ServerPlayNetworking.registerGlobalReceiver(RingAtlasPregenerationControlPayload.ID, (payload, context) -> context.server().execute(() -> {
            RingTerrainAtlasServer.controlPregeneration(context.player(), payload.worldHash(), payload.action());
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            RingTerrainAtlasServer.clearPlayer(handler.player);
        });
    }

    private static void sendSettings(ServerGamePacketListenerImpl handler, ServerLevel level) {
        if (!ServerPlayNetworking.canSend(handler.player, RingSettingsPayload.ID)) {
            handler.disconnect(Component.literal("RingWorld client is missing or out of date. Install a matching RingWorld client version."));
            return;
        }
        RingWorldConfig settings = RingWorldConfig.get(level);
        ServerPlayNetworking.send(handler.player, RingSettingsPayload.from(level.dimension(), settings, level.getSeed(), settings.layoutFingerprint(level.getSeed())));
        RingTerrainAtlasServer.sendMetadata(handler.player);
        RingTerrainAtlasServer.sendBuildingSettings(handler.player);
    }
}
