package dev.ringworld.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.net.*;
import dev.ringworld.world.RingLayoutFingerprint;
import dev.ringworld.world.RingLodCommandSuggestions;
import dev.ringworld.world.atlas.RingAtlasLightProfile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

public final class RingWorldClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RenderPipelines.register(RingSurfaceTextureRenderer.pipeline());
        registerAtlasLightCommand();
        RingClientPayloadTransport.configure(new FabricRingClientPayloadTransport());
        ClientPlayNetworking.registerGlobalReceiver(RingSettingsPayload.ID, (payload, context) -> context.client().execute(() -> {
            long fingerprint = RingLayoutFingerprint.compute(payload.getConfig());
            if (fingerprint != payload.fingerprint()) {
                var handler = context.client().getConnection();
                if (handler != null) {
                    handler.getConnection().disconnect(Component.literal("RingWorld layout fingerprint mismatch."));
                }
                return;
            }
            clearRingSession();
            ClientRingState.set(payload.dimension(), payload.getConfig(), payload.seed(), fingerprint);
        }));
        ClientPlayNetworking.registerGlobalReceiver(RingBuildingSettingsPayload.ID, (payload, context) -> context.client().execute(() -> ClientRingState.setBuildingSettings(payload)));
        ClientPlayNetworking.registerGlobalReceiver(RingTerrainAtlasMetadataPayload.ID, (payload, context) -> context.client().execute(() -> {
            boolean cacheComplete = ClientRingState.installTerrainAtlas(payload);
            if (!RingClientPayloadTransport.canSend(RingTerrainAtlasRequestPayload.ID)) {
                var handler = context.client().getConnection();
                if (handler != null) {
                    handler.getConnection().disconnect(Component.literal("Server RingWorld terrain-atlas protocol is missing or out of date."));
                }
                return;
            }
            RingClientPayloadTransport.send(new RingTerrainAtlasRequestPayload(payload.worldHash(), payload.dimension(), ClientRingState.terrainAtlasDurableRevision(), cacheComplete));
        }));
        ClientPlayNetworking.registerGlobalReceiver(RingTerrainAtlasTilePayload.ID, (payload, context) -> context.client().execute(() -> ClientRingState.applyTerrainAtlasTile(payload.worldHash(), payload.tileX(), payload.tileZ(), payload.data())));
        ClientPlayNetworking.registerGlobalReceiver(RingTerrainAtlasRevisionPayload.ID, (payload, context) -> context.client().execute(() -> ClientRingState.commitTerrainAtlasRevision(payload.worldHash(), payload.revision())));
        ClientPlayNetworking.registerGlobalReceiver(RingTerrainPreviewPayload.ID, (payload, context) -> context.client().execute(() -> ClientRingState.installTerrainPreview(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(RingWorldClient::clearRingSession));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            RingAtlasPerformanceFeedback.tick(client);
            if (client.getConnection() != null && ClientCommands.getActiveDispatcher() != null) {
                RingLodCommandSuggestions.ensurePresent(client.getConnection().getCommands());
            }
            if (client.player != null) ClientRingState.updateCameraPosition(client.player.getX());
        });
    }

    private static void registerAtlasLightCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var distortion = ClientCommands.literal("distortion").executes(context -> {
                String message = RingDistortionTuning.summary();
                context.getSource().sendFeedback(Component.literal(message));
                return 1;
            });
            for (String action : new String[]{"on", "off", "show"}) {
                distortion.then(ClientCommands.literal(action).executes(context -> {
                    String message = action.equals("show") ? RingDistortionTuning.summary() : RingDistortionTuning.select(action.equals("on"));
                    context.getSource().sendFeedback(Component.literal(message));
                    return 1;
                }));
            }
            distortion.then(ClientCommands.literal("distance").then(ClientCommands.argument("chunks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 8)).executes(context -> {
                String message = RingDistortionTuning.distance(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "chunks"));
                context.getSource().sendFeedback(Component.literal(message));
                return 1;
            })));
            dispatcher.register(ClientCommands.literal("ringworld").then(distortion));
            var lod = ClientCommands.literal("lod").executes(context -> {
                String message = RingClientLodTuning.summary();
                context.getSource().sendFeedback(Component.literal(message));
                return 1;
            });
            lod.then(ClientCommands.literal("show").executes(context -> {
                String message = RingClientLodTuning.summary();
                context.getSource().sendFeedback(Component.literal(message));
                return 1;
            }));
            lod.then(ClientCommands.literal("reset").executes(context -> {
                String message = RingClientLodTuning.select(null);
                context.getSource().sendFeedback(Component.literal(message));
                return 1;
            }));
            for (var quality : dev.ringworld.world.RingLodQuality.values()) {
                lod.then(ClientCommands.literal(quality.command()).executes(context -> {
                    String message = RingClientLodTuning.select(quality);
                    context.getSource().sendFeedback(Component.literal(message));
                    return 1;
                }));
            }
            dispatcher.register(ClientCommands.literal("ringworld").then(lod));
            // The client root must explicitly forward server-owned building commands.
            var outside = ClientCommands.literal("outside");
            for (String operation : new String[]{"on", "off", "show"}) {
                outside.then(ClientCommands.literal(operation).executes(context -> {
                    var connection = Minecraft.getInstance().getConnection();
                    if (connection == null) return 0;
                    connection.send(new net.minecraft.network.protocol.game.ServerboundChatCommandPacket(context.getInput()));
                    return 1;
                }));
            }
            dispatcher.register(ClientCommands.literal("ringworld").then(ClientCommands.literal("building").then(outside)));

            var falloff = ClientCommands.argument("falloff", FloatArgumentType.floatArg(RingAtlasLightProfile.MIN_FALLOFF, RingAtlasLightProfile.MAX_FALLOFF)).executes(context -> setGamma(context.getSource(), FloatArgumentType.getFloat(context, "falloff"), RingAtlasLightProfile.DEFAULT_GAMMA_PEAK));
            falloff.then(ClientCommands.argument("peak", FloatArgumentType.floatArg(RingAtlasLightProfile.MIN_PEAK, RingAtlasLightProfile.MAX_PEAK)).executes(context -> setGamma(context.getSource(), FloatArgumentType.getFloat(context, "falloff"), FloatArgumentType.getFloat(context, "peak"))));
            var ringLights = ClientCommands.literal("ringlights").executes(context -> showAtlasLight(context.getSource())).then(ClientCommands.literal("show").executes(context -> showAtlasLight(context.getSource()))).then(ClientCommands.literal("reset").executes(context -> {
                var profile = RingAtlasLightTuning.reset();
                context.getSource().sendFeedback(Component.literal("Ring Atlas light: " + profile.summary()));
                return 1;
            })).then(falloff);
            dispatcher.register(ClientCommands.literal("ringworld").then(ringLights));
        });
    }

    private static int showAtlasLight(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("Ring Atlas light: " + RingAtlasLightTuning.profile().summary()));
        return 1;
    }

    private static int setGamma(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, float falloff, float peak) {
        var profile = RingAtlasLightTuning.useGamma(falloff, peak);
        source.sendFeedback(Component.literal("Ring Atlas light: " + profile.summary()));
        return 1;
    }

    /**
     * Releases all client-owned state for the previous RingWorld session.
     * Called from both Fabric's network lifecycle and Minecraft's local-world
     * teardown path because an integrated-server exit does not always deliver
     * the play-connection disconnect event before the next world is opened.
     */
    public static void clearRingSession() {
        RingSurfaceTextureRenderer.clear();
        RingClientLodTuning.clearSession();
        RingDistortionTuning.clearSession();
        ClientRingState.clear();
    }
}
