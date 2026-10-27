package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Requests an immediate authoritative status and starts observing progress.
 */
public record RingAtlasPregenerationStatusRequestPayload(long worldHash) implements CustomPacketPayload {
    public static final Type<RingAtlasPregenerationStatusRequestPayload> ID = new Type<>(RingWorldMod.id("atlas_pregen_status_request_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingAtlasPregenerationStatusRequestPayload> CODEC = StreamCodec.composite(ByteBufCodecs.LONG, RingAtlasPregenerationStatusRequestPayload::worldHash, RingAtlasPregenerationStatusRequestPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
