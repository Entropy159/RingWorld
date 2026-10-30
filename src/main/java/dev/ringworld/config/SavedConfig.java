package dev.ringworld.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ringworld.RingWorldMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class SavedConfig extends SavedData {
    private static final Codec<SavedConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(Codec.STRING.fieldOf("data").forGetter(SavedConfig::toData)).apply(instance, SavedConfig::fromData));
    private static final SavedDataType<SavedConfig> TYPE = new SavedDataType<>(RingWorldMod.id("config"), SavedConfig::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final RingWorldConfig config;

    private SavedConfig() {
        this(ConfigLoader.load(null));
        RingWorldMod.LOGGER.warn("Saved config loaded the default config, this may be a bug!");
        Thread.dumpStack();
    }

    private SavedConfig(RingWorldConfig config) {
        this.config = config;
        setDirty();
    }

    public static SavedConfig fromData(String data) {
        return new SavedConfig(ConfigLoader.fromData(data));
    }

    public String toData() {
        return ConfigLoader.toData(config);
    }

    public RingWorldConfig getConfig() {
        return config;
    }

    public static SavedConfig get(ServerLevel world) {
        var config = world.getDataStorage().get(TYPE);
        if (config == null) {
            config = new SavedConfig(ConfigLoader.load(world.dimension()));
            world.getDataStorage().set(TYPE, config);
        }
        return config;
    }
}
