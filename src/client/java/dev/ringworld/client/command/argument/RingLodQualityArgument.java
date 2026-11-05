package dev.ringworld.client.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.ringworld.RingWorldMod;
import dev.ringworld.client.RingLodQuality;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class RingLodQualityArgument implements ArgumentType<RingLodQuality> {
    public static void register() {
        ArgumentTypeRegistry.registerArgumentType(RingWorldMod.id("lod_quality"), RingLodQualityArgument.class, SingletonArgumentInfo.contextFree(RingLodQualityArgument::new));
    }

    @Override
    public RingLodQuality parse(StringReader reader) throws CommandSyntaxException {
        try {
            String name = reader.readString();
            return RingLodQuality.valueOf(name.toUpperCase());
        } catch (Exception e) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException().create("Invalid LOD quality.");
        }
    }

    public static RingLodQuality get(CommandContext<FabricClientCommandSource> ctx, String name) {
        return ctx.getArgument(name, RingLodQuality.class);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        for (var quality : RingLodQuality.values()) {
            String value = quality.name().toLowerCase();
            if (value.contains(builder.getInput().substring(builder.getStart()))) {
                builder.suggest(value);
            }
        }
        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return Arrays.stream(RingLodQuality.values()).map(quality -> quality.name().toLowerCase()).toList();
    }
}
