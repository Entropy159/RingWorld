package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import dev.ringworld.server.atlas.RingTerrainAtlasServer;
import dev.ringworld.server.atlas.pregen.HeadlessPrewarmCoordinator;
import dev.ringworld.world.RingSkySettings;
import dev.ringworld.world.RingWorldSettings;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.UUID;

/**
 * Performs the mandatory client-mod handshake and ships immutable settings.
 */
public final class RingWorldNetworking {
    private static final RingHandshakeTracker HANDSHAKES = new RingHandshakeTracker();

    private RingWorldNetworking() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(RingSettingsPayload.ID, RingSettingsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingSkyProfilePayload.ID, RingSkyProfilePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingBuildingSettingsPayload.ID, RingBuildingSettingsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RingBuildingControlPayload.ID, RingBuildingControlPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RingSettingsAckPayload.ID, RingSettingsAckPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingTerrainAtlasMetadataPayload.ID, RingTerrainAtlasMetadataPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingTerrainAtlasTilePayload.ID, RingTerrainAtlasTilePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingTerrainAtlasRevisionPayload.ID, RingTerrainAtlasRevisionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingTerrainPreviewPayload.ID, RingTerrainPreviewPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RingTerrainAtlasRequestPayload.ID, RingTerrainAtlasRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RingAtlasPregenerationStatusRequestPayload.ID, RingAtlasPregenerationStatusRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RingAtlasPregenerationControlPayload.ID, RingAtlasPregenerationControlPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingAtlasPregenerationStatusPayload.ID, RingAtlasPregenerationStatusPayload.CODEC);
    }

    public static void registerServer() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (HeadlessPrewarmCoordinator.rejectPlayerJoins(server)) return;
            sendSettings(handler);
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
            if (HeadlessPrewarmCoordinator.rejectPlayerJoins(player.level().getServer())) return;
            sendSettings(player.connection);
        });
        ServerPlayNetworking.registerGlobalReceiver(RingBuildingControlPayload.ID, (payload, context) -> context.server().execute(() -> {
            if (!requireAcknowledged(context.player())) return;
            RingTerrainAtlasServer.controlBuilding(context.player(), payload.fingerprint(), payload.enabled());
        }));
        ServerPlayNetworking.registerGlobalReceiver(RingSettingsAckPayload.ID, (payload, context) -> context.server().execute(() -> validateAcknowledgement(payload, context.player().connection)));
        ServerPlayNetworking.registerGlobalReceiver(RingTerrainAtlasRequestPayload.ID, (payload, context) -> context.server().execute(() -> {
            if (!requireAcknowledged(context.player())) return;
            RingTerrainAtlasServer.requestTiles(context.player(), payload.worldHash(), payload.revision(), payload.cacheComplete());
        }));
        ServerPlayNetworking.registerGlobalReceiver(RingAtlasPregenerationStatusRequestPayload.ID, (payload, context) -> context.server().execute(() -> {
            if (!requireAcknowledged(context.player())) return;
            RingTerrainAtlasServer.requestPregenerationStatus(context.player(), payload.worldHash());
        }));
        ServerPlayNetworking.registerGlobalReceiver(RingAtlasPregenerationControlPayload.ID, (payload, context) -> context.server().execute(() -> {
            if (!requireAcknowledged(context.player())) return;
            RingTerrainAtlasServer.controlPregeneration(context.player(), payload.worldHash(), payload.action());
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            HANDSHAKES.clear(handler.player.getUUID());
            RingTerrainAtlasServer.clearPlayer(handler.player);
        });
        ServerTickEvents.END_SERVER_TICK.register(RingWorldNetworking::expireAcknowledgements);
    }

    private static void sendSettings(ServerGamePacketListenerImpl handler) {
        ServerLevel level = handler.player.level();
        if (!ServerPlayNetworking.canSend(handler.player, RingSettingsPayload.ID)) {
            handler.disconnect(Component.literal("RingWorld client is missing or out of date. Install a matching RingWorld client version."));
            return;
        }
        if (!ServerPlayNetworking.canSend(handler.player, RingTerrainAtlasMetadataPayload.ID) || !ServerPlayNetworking.canSend(handler.player, RingSkyProfilePayload.ID) || !ServerPlayNetworking.canSend(handler.player, RingBuildingSettingsPayload.ID) || !ServerPlayNetworking.canSend(handler.player, RingTerrainAtlasTilePayload.ID) || !ServerPlayNetworking.canSend(handler.player, RingTerrainAtlasRevisionPayload.ID) || !ServerPlayNetworking.canSend(handler.player, RingTerrainPreviewPayload.ID) || !ServerPlayNetworking.canSend(handler.player, RingAtlasPregenerationStatusPayload.ID)) {
            handler.disconnect(Component.literal("RingWorld client feature channels are missing or out of date. Install the matching version."));
            return;
        }
        RingWorldSettings settings = RingWorldSettings.get(level);
        HANDSHAKES.begin(handler.player.getUUID(), handler.player.level().getServer().getTickCount());
        ServerPlayNetworking.send(handler.player, RingSettingsHandshake.payloadFor(level.dimension(), settings, RingSkySettings.get(level).profile()));
    }

    private static void validateAcknowledgement(RingSettingsAckPayload payload, ServerGamePacketListenerImpl handler) {
        ServerLevel level = handler.player.level();
        RingWorldSettings settings = RingWorldSettings.get(level);
        if (!RingSettingsHandshake.accepts(settings, payload)) {
            HANDSHAKES.clear(handler.player.getUUID());
            handler.disconnect(Component.literal("RingWorld geometry/protocol acknowledgement mismatch."));
            return;
        }
        RingHandshakeTracker.AcknowledgementResult result = HANDSHAKES.acknowledge(handler.player.getUUID());
        if (result == RingHandshakeTracker.AcknowledgementResult.UNEXPECTED) {
            handler.disconnect(Component.literal("RingWorld settings acknowledgement was unexpected or expired. Reconnect with a matching client."));
            return;
        }
        if (result == RingHandshakeTracker.AcknowledgementResult.ALREADY_ACKNOWLEDGED) {
            RingWorldMod.LOGGER.debug("Ignoring duplicate RingWorld settings acknowledgement from {}", handler.player.getName().getString());
            return;
        }
        RingWorldMod.LOGGER.info("RingWorld settings acknowledged by {}: {}x{}, format {}", handler.player.getName().getString(), settings.circumferenceBlocks(), settings.widthBlocks(), payload.formatVersion());
        RingTerrainAtlasServer.sendMetadata(handler.player);
        RingTerrainAtlasServer.sendBuildingSettings(handler.player);
    }

    private static boolean requireAcknowledged(ServerPlayer player) {
        if (HANDSHAKES.isAcknowledged(player.getUUID())) return true;
        player.connection.disconnect(Component.literal("RingWorld handshake is incomplete; reconnect with a matching client."));
        return false;
    }

    private static void expireAcknowledgements(MinecraftServer server) {
        for (UUID playerId : HANDSHAKES.expire(server.getTickCount())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            RingWorldMod.LOGGER.warn("RingWorld settings acknowledgement timed out for {}", player.getName().getString());
            player.connection.disconnect(Component.literal("RingWorld handshake timed out. Install the matching client mod and reconnect."));
        }
    }
}
