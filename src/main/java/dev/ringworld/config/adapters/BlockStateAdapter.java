package dev.ringworld.config.adapters;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.util.stream.Collectors;

public class BlockStateAdapter extends TypeAdapter<BlockState> {
    @Override
    public void write(JsonWriter out, BlockState state) throws IOException {
        if (state == null) {
            out.nullValue();
            return;
        }

        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        StringBuilder result = new StringBuilder();
        result.append(id);
        if (!state.isSingletonState()) {
            result.append("[");
            result.append(state.getValues().map(Property.Value::toString).collect(Collectors.joining(",")));
            result.append("]");
        }

        out.value(result.toString());
    }

    @Override
    public BlockState read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, in.nextString(), true).blockState();
        } catch (CommandSyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
