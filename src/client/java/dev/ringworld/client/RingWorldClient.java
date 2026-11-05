package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.command.ClientConfigCommand;
import dev.ringworld.client.command.argument.RingLodQualityArgument;
import dev.ringworld.client.config.ClientRingWorldConfig;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.net.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

public final class RingWorldClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RenderPipelines.register(RingSurfaceTextureRenderer.pipeline());

        ClientPlayNetworking.registerGlobalReceiver(RingSettingsPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            long fingerprint = payload.getConfig().layoutFingerprint(payload.seed());
            if (fingerprint != payload.fingerprint()) {
                RingWorldMod.LOGGER.error("RingWorld client fingerprint did not match server! Expected {}, got {}", payload.fingerprint(), fingerprint);
                var handler = context.client().getConnection();
                if (handler != null) {
                    handler.getConnection().disconnect(Component.literal("RingWorld layout fingerprint mismatch."));
                }
                return;
            }
            clearRingSession();
            ClientRingState.set(payload.dimension(), payload.getConfig(), payload.seed(), fingerprint);
        }));
        ClientPlayNetworking.registerGlobalReceiver(RingTerrainAtlasMetadataPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            boolean cacheComplete = ClientRingState.installTerrainAtlas(payload);
            if (!ClientPlayNetworking.canSend(RingTerrainAtlasRequestPayload.TYPE)) {
                var handler = context.client().getConnection();
                if (handler != null) {
                    handler.getConnection().disconnect(Component.literal("Server RingWorld terrain-atlas protocol is missing or out of date."));
                }
                return;
            }
            if (ClientRingWorldConfig.get().fullAtlas()) {
                ClientPlayNetworking.send(new RingTerrainAtlasRequestPayload(payload.worldHash(), payload.dimension(), ClientRingState.terrainAtlasDurableRevision(), cacheComplete));
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(RingTerrainAtlasTilePayload.TYPE, (payload, context) -> context.client().execute(() -> {
            if (ClientRingWorldConfig.get().fullAtlas())
                ClientRingState.applyTerrainAtlasTile(payload.worldHash(), payload.tileX(), payload.tileZ(), payload.data());
        }));
        ClientPlayNetworking.registerGlobalReceiver(RingTerrainAtlasRevisionPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            if (ClientRingWorldConfig.get().fullAtlas())
                ClientRingState.commitTerrainAtlasRevision(payload.worldHash(), payload.revision());
        }));
        ClientPlayNetworking.registerGlobalReceiver(RingTerrainPreviewPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            if (ClientRingWorldConfig.get().enableAtlas()) ClientRingState.installTerrainPreview(payload);
        }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(RingWorldClient::clearRingSession));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) ClientRingState.updateCameraPosition(client.player.getX());
            ClientRingState.saveTerrainAtlasIfDue(false);
        });

        RingLodQualityArgument.register();
        ClientCommandRegistrationCallback.EVENT.register(((dispatcher, context) -> ClientConfigCommand.register(dispatcher)));

        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, RingWorldMod.id("hud"), (graphics, delta) -> {
            String prefix = "Ring Atlas Generating: ";
            float scale = 0.5f;
            int left = 12;
            int top = 12;
            int padding = 3;
            int background = 0xA0000000;
            int color = 0xFFFFFFFF;

            var atlas = ClientRingState.terrainAtlas();
            if (atlas == null || atlas.presentCount() == atlas.cellCount()) return;
            int percent = Math.min(99, (int) (atlas.presentCount() * 100L / atlas.cellCount()));
            String label = prefix + percent + "%";
            Component text = Component.literal(label);
            int width = Minecraft.getInstance().font.width(text);
            graphics.pose().pushMatrix();
            graphics.pose().scale(scale, scale);
            graphics.fill(left, top, left + width + padding * 2, top + Minecraft.getInstance().font.lineHeight + padding * 2, background);
            graphics.text(Minecraft.getInstance().font, text, left + padding, top + padding, color);
            graphics.pose().popMatrix();
        });
    }

    /**
     * Releases all client-owned state for the previous RingWorld session.
     * Called from both Fabric's network lifecycle and Minecraft's local-world
     * teardown path because an integrated-server exit does not always deliver
     * the play-connection disconnect event before the next world is opened.
     */
    public static void clearRingSession() {
        RingSurfaceTextureRenderer.clear();
        ClientRingState.clear();
    }
}
