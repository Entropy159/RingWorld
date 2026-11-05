package dev.ringworld.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.ringworld.client.command.argument.RingLodQualityArgument;
import dev.ringworld.client.config.ClientRingWorldConfig;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class ClientConfigCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ringworld-client")
                .then(literal("distortion")
                        .then(argument("chunks", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    ClientRingWorldConfig.update(ClientRingWorldConfig.get().withDistortionChunks(IntegerArgumentType.getInteger(ctx, "chunks")));
                                    return 1;
                                })))
                .then(literal("lodQuality")
                        .then(argument("quality", new RingLodQualityArgument())
                                .executes(ctx -> {
                                    ClientRingWorldConfig.update(ClientRingWorldConfig.get().withLodQuality(RingLodQualityArgument.get(ctx, "quality")));
                                    RingSurfaceTextureRenderer.clear();
                                    return 1;
                                })))
                .then(literal("toggleAtlas")
                        .executes(ctx -> {
                            var enabled = !ClientRingWorldConfig.get().enableAtlas();
                            ClientRingWorldConfig.update(ClientRingWorldConfig.get().withEnableAtlas(enabled));
                            RingSurfaceTextureRenderer.clear();
                            return 1;
                        }))
                .then(literal("cloudOffset")
                        .then(argument("offset", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    ClientRingWorldConfig.update(ClientRingWorldConfig.get().withCloudHeightOffset(IntegerArgumentType.getInteger(ctx, "offset")));
                                    return 1;
                                })))
                .then(literal("cloudFade")
                        .then(argument("blocks", FloatArgumentType.floatArg(0))
                                .executes(ctx -> {
                                    ClientRingWorldConfig.update(ClientRingWorldConfig.get().withCloudEdgeFade(FloatArgumentType.getFloat(ctx, "blocks")));
                                    return 1;
                                }))));
    }
}
