package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Commits an atlas revision after every preceding changed tile has been sent.
 */
public record RingTerrainAtlasRevisionPayload(long worldHash, long revision) implements CustomPacketPayload {
    public static final Type<RingTerrainAtlasRevisionPayload> ID = new Type<>(RingWorldMod.id("terrain_atlas_revision_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingTerrainAtlasRevisionPayload> CODEC = StreamCodec.composite(ByteBufCodecs.LONG, RingTerrainAtlasRevisionPayload::worldHash, ByteBufCodecs.VAR_LONG, RingTerrainAtlasRevisionPayload::revision, RingTerrainAtlasRevisionPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
