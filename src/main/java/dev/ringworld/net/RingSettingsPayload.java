package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import dev.ringworld.config.ConfigLoader;
import dev.ringworld.config.RingWorldConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

public record RingSettingsPayload(ResourceKey<Level> dimension, String data, long seed, long fingerprint) implements CustomPacketPayload {
    public static final Type<RingSettingsPayload> ID = new Type<>(RingWorldMod.id("settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingSettingsPayload> CODEC = StreamCodec.composite(ResourceKey.streamCodec(Registries.DIMENSION), RingSettingsPayload::dimension, ByteBufCodecs.STRING_UTF8, RingSettingsPayload::data, ByteBufCodecs.LONG, RingSettingsPayload::seed, ByteBufCodecs.LONG, RingSettingsPayload::fingerprint, RingSettingsPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static CustomPacketPayload from(ResourceKey<Level> dimension, RingWorldConfig settings, long seed, long fingerprint) {
        return new RingSettingsPayload(dimension, ConfigLoader.toData(settings), seed, fingerprint);
    }

    public RingWorldConfig getConfig() {
        return ConfigLoader.fromData(data);
    }
}
