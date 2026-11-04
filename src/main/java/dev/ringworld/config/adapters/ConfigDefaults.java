package dev.ringworld.config.adapters;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.ringworld.config.RingWorldConfig;

import java.io.IOException;

public class ConfigDefaults implements TypeAdapterFactory {
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (type.getRawType() != RingWorldConfig.class) {
            return null;
        }

        @SuppressWarnings("unchecked") T defaults = (T) RingWorldConfig.DEFAULT;

        TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

        return new TypeAdapter<>() {
            @Override
            public T read(JsonReader in) {
                JsonElement json = JsonParser.parseReader(in);

                if (json.isJsonNull()) {
                    return null;
                }

                JsonObject merged = delegate.toJsonTree(defaults).getAsJsonObject();

                JsonObject input = json.getAsJsonObject();

                input.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));

                return delegate.fromJsonTree(merged);
            }

            @Override
            public void write(JsonWriter out, T value) throws IOException {
                delegate.write(out, value);
            }
        };
    }
}
