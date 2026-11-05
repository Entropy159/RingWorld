package dev.ringworld.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.ringworld.client.command.argument.RingLodQualityArgument;
import dev.ringworld.client.config.ClientRingWorldConfig;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class ClientConfigCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ringworld-client")
                .then(literal("distortion")
                        .then(argument("chunks", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    int chunks = IntegerArgumentType.getInteger(ctx, "chunks");
                                    ClientRingWorldConfig.update(ClientRingWorldConfig.get().withDistortionChunks(chunks));
                                    ctx.getSource().sendFeedback(Component.literal("Set distortion chunks to " + chunks));
                                    return 1;
                                })))
                .then(literal("lodQuality")
                        .then(argument("quality", new RingLodQualityArgument())
                                .executes(ctx -> {
                                    var quality = RingLodQualityArgument.get(ctx, "quality");
                                    ClientRingWorldConfig.update(ClientRingWorldConfig.get().withLodQuality(quality));
                                    RingSurfaceTextureRenderer.clear();
                                    ctx.getSource().sendFeedback(Component.literal("Set LOD quality to " + quality));
                                    return 1;
                                })))
                .then(literal("toggleAtlas")
                        .executes(ctx -> {
                            var enabled = !ClientRingWorldConfig.get().enableAtlas();
                            ClientRingWorldConfig.update(ClientRingWorldConfig.get().withEnableAtlas(enabled));
                            RingSurfaceTextureRenderer.clear();
                            ctx.getSource().sendFeedback(Component.literal("Set atlas enabled: " + enabled));
                            return 1;
                        }))
                .then(literal("cloudOffset")
                        .then(argument("offset", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    int offset = IntegerArgumentType.getInteger(ctx, "offset");
                                    ClientRingWorldConfig.update(ClientRingWorldConfig.get().withCloudHeightOffset(offset));
                                    ctx.getSource().sendFeedback(Component.literal("Set cloud height offset to " + offset));
                                    return 1;
                                })))
                .then(literal("cloudFade")
                        .then(argument("blocks", FloatArgumentType.floatArg(0))
                                .executes(ctx -> {
                                    float fade = FloatArgumentType.getFloat(ctx, "blocks");
                                    ClientRingWorldConfig.update(ClientRingWorldConfig.get().withCloudEdgeFade(fade));
                                    ctx.getSource().sendFeedback(Component.literal("Set cloud fade to " + fade + " blocks"));
                                    return 1;
                                })))
                .then(literal("seedOnly")
                        .executes(ctx -> {
                            boolean onlySeed = !ClientRingWorldConfig.get().onlySeedPreview();
                            ClientRingWorldConfig.update(ClientRingWorldConfig.get().withOnlySeedPreview(onlySeed));
                            ctx.getSource().sendFeedback(Component.literal("Set seed-only to " + onlySeed));
                            return 1;
                        })));
    }
}
