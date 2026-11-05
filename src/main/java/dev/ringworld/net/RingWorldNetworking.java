package dev.ringworld.net;

import dev.ringworld.config.RingWorldConfig;
import dev.ringworld.server.atlas.RingTerrainAtlasServer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Performs the mandatory client-mod handshake and ships immutable settings.
 */
public final class RingWorldNetworking {
    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(RingSettingsPayload.TYPE, RingSettingsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingTerrainAtlasMetadataPayload.TYPE, RingTerrainAtlasMetadataPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingTerrainAtlasTilePayload.TYPE, RingTerrainAtlasTilePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingTerrainAtlasRevisionPayload.TYPE, RingTerrainAtlasRevisionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingTerrainPreviewPayload.TYPE, RingTerrainPreviewPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RingTerrainAtlasRequestPayload.TYPE, RingTerrainAtlasRequestPayload.CODEC);
    }

    public static void registerServer() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sendSettings(handler.player);
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
            sendSettings(player);
        });
        ServerPlayNetworking.registerGlobalReceiver(RingTerrainAtlasRequestPayload.TYPE, (payload, context) -> context.server().execute(() -> {
            RingTerrainAtlasServer.requestTiles(context.player(), payload.worldHash(), payload.revision(), payload.cacheComplete());
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            RingTerrainAtlasServer.clearPlayer(handler.player);
        });
    }

    private static void sendSettings(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, RingSettingsPayload.TYPE)) {
            player.connection.disconnect(Component.literal("RingWorld client is missing or out of date. Install a matching RingWorld client version."));
            return;
        }
        var level = player.level();
        RingWorldConfig settings = RingWorldConfig.get(level);
        ServerPlayNetworking.send(player, RingSettingsPayload.from(level.dimension(), settings, level.getSeed(), settings.layoutFingerprint(level.getSeed())));
        RingTerrainAtlasServer.sendMetadata(player);
    }
}
