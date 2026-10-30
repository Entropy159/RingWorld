package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NonNull;

public record RingBuildingSettingsPayload(long fingerprint, boolean enabled) implements CustomPacketPayload {
    public static final Type<RingBuildingSettingsPayload> ID = new Type<>(RingWorldMod.id("building_settings_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingBuildingSettingsPayload> CODEC = StreamCodec.composite(ByteBufCodecs.LONG, RingBuildingSettingsPayload::fingerprint, ByteBufCodecs.BOOL, RingBuildingSettingsPayload::enabled, RingBuildingSettingsPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
