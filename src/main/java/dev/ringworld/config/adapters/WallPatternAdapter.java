package dev.ringworld.config.adapters;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.ringworld.world.wall.RingWallPattern;

import java.io.IOException;

public class WallPatternAdapter extends TypeAdapter<RingWallPattern> {
    @Override
    public void write(JsonWriter out, RingWallPattern value) throws IOException {
        out.value(value.id);
    }

    @Override
    public RingWallPattern read(JsonReader in) throws IOException {
        return RingWallPattern.getPattern(in.nextString());
    }
}
