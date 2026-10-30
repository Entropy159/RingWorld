package dev.ringworld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ringworld.RingWorldMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ConfigLoader {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public static Path getConfigPath(ResourceKey<Level> dimension) throws IOException {
        var configFolder = FabricLoader.getInstance().getConfigDir().resolve(RingWorldMod.MOD_ID);
        Files.createDirectories(configFolder);
        if (dimension == null) {
            return configFolder.resolve("default.json");
        }
        var namespaceFolder = configFolder.resolve(dimension.identifier().getNamespace());
        return namespaceFolder.resolve(dimension.identifier().getPath() + ".json");
    }

    public static RingWorldConfig load(ResourceKey<Level> dimension) {
        try {
            Path configPath = getConfigPath(dimension);
            if (dimension != null) RingWorldMod.LOGGER.info("Loading config for {} at {}", dimension.identifier(), configPath);
            File configFile = configPath.toFile();
            RingWorldConfig config = new RingWorldConfig();
            if (configFile.exists()) {
                String data = Files.readString(configPath, StandardCharsets.UTF_8);
                config = fromData(data);
            } else if (dimension != null) {
                RingWorldMod.LOGGER.info("Didn't find a config override for dimension {}, falling back to default", dimension.identifier());
                return load(null);
            }
            save(dimension, config);
            return config;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static RingWorldConfig fromData(String data) {
        return GSON.fromJson(data, RingWorldConfig.class);
    }

    public static void save(ResourceKey<Level> dimension, RingWorldConfig config) throws IOException {
        Path configPath = getConfigPath(dimension);
        Files.writeString(configPath, toData(config), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static String toData(RingWorldConfig config) {
        return GSON.toJson(config);
    }
}
