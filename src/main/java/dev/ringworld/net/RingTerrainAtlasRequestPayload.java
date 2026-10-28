package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

/**
 * Lets a client reuse a complete atlas cached by world hash.
 */
public record RingTerrainAtlasRequestPayload(long worldHash, ResourceKey<Level> dimension, long revision,
                                             boolean cacheComplete) implements CustomPacketPayload {
    public static final Type<RingTerrainAtlasRequestPayload> ID = new Type<>(RingWorldMod.id("terrain_atlas_request_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingTerrainAtlasRequestPayload> CODEC = StreamCodec.composite(ByteBufCodecs.LONG, RingTerrainAtlasRequestPayload::worldHash, ResourceKey.streamCodec(Registries.DIMENSION), RingTerrainAtlasRequestPayload::dimension, ByteBufCodecs.VAR_LONG, RingTerrainAtlasRequestPayload::revision, ByteBufCodecs.BOOL, RingTerrainAtlasRequestPayload::cacheComplete, RingTerrainAtlasRequestPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
