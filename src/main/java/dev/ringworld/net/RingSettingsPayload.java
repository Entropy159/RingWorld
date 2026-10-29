package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingSkyProfile;
import dev.ringworld.world.RingWallStyle;
import dev.ringworld.world.RingWorldGenerationSettings;
import dev.ringworld.world.RingWorldLayout;
import dev.ringworld.world.atlas.RingAtlasFidelity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

/**
 * Complete immutable layout sent before the client renders a ring world.
 */
public record RingSettingsPayload(ResourceKey<Level> dimension, int width, int circumference, long seed, int wallHeight,
                                  int surfaceReferenceY, int terrainNoiseMapping, RingWallStyle wallStyle,
                                  RingSkyProfile skyProfile, RingWorldGenerationSettings generationSettings,
                                  float sunAngleOffsetY, float sunAngleOffsetZ, int formatVersion,
                                  long fingerprint) implements CustomPacketPayload {
    public static final Type<RingSettingsPayload> ID = new Type<>(RingWorldMod.id("settings_v7"));
    private static final StreamCodec<RegistryFriendlyByteBuf, RingWallStyle> WALL_STYLE_CODEC = StreamCodec.ofMember((style, buffer) -> {
        buffer.writeVarInt(style.thicknessBlocks());
        buffer.writeVarInt(style.palette().id());
        buffer.writeVarInt(style.pattern().id());
        buffer.writeVarInt(style.decayPercent());
        buffer.writeVarInt(style.formatVersion());
    }, buffer -> new RingWallStyle(buffer.readVarInt(), RingWallStyle.Palette.fromId(buffer.readVarInt()), RingWallStyle.Pattern.fromId(buffer.readVarInt()), buffer.readVarInt(), buffer.readVarInt()));
    private static final StreamCodec<RegistryFriendlyByteBuf, RingSkyProfile> SKY_PROFILE_CODEC = StreamCodec.ofMember((profile, buffer) -> {
        buffer.writeVarInt(profile.backdrop().id());
        buffer.writeVarInt(profile.lightSource().id());
        buffer.writeVarInt(profile.formatVersion());
    }, buffer -> new RingSkyProfile(RingSkyProfile.Backdrop.fromId(buffer.readVarInt()), RingSkyProfile.LightSource.fromId(buffer.readVarInt()), buffer.readVarInt()));
    private static final StreamCodec<RegistryFriendlyByteBuf, RingWorldGenerationSettings> GENERATION_SETTINGS_CODEC = StreamCodec.ofMember((settings, buffer) -> {
        buffer.writeVarInt(settings.atlasFidelity().id());
        buffer.writeVarInt(settings.layout().id());
        buffer.writeBoolean(settings.continuousRiver());
        buffer.writeBoolean(settings.moreStructures());
        buffer.writeVarInt(settings.formatVersion());
    }, buffer -> new RingWorldGenerationSettings(RingAtlasFidelity.fromId(buffer.readVarInt()), RingWorldLayout.fromId(buffer.readVarInt()), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt()));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingSettingsPayload> CODEC = StreamCodec.of((buf, val) -> {
        ResourceKey.streamCodec(Registries.DIMENSION).encode(buf, val.dimension);
        ByteBufCodecs.VAR_INT.encode(buf, val.width);
        ByteBufCodecs.VAR_INT.encode(buf, val.circumference);
        ByteBufCodecs.LONG.encode(buf, val.seed);
        ByteBufCodecs.VAR_INT.encode(buf, val.wallHeight);
        ByteBufCodecs.VAR_INT.encode(buf, val.surfaceReferenceY);
        ByteBufCodecs.VAR_INT.encode(buf, val.terrainNoiseMapping);
        WALL_STYLE_CODEC.encode(buf, val.wallStyle);
        SKY_PROFILE_CODEC.encode(buf, val.skyProfile);
        GENERATION_SETTINGS_CODEC.encode(buf, val.generationSettings);
        ByteBufCodecs.FLOAT.encode(buf, val.sunAngleOffsetY);
        ByteBufCodecs.FLOAT.encode(buf, val.sunAngleOffsetZ);
        ByteBufCodecs.VAR_INT.encode(buf, val.formatVersion);
        ByteBufCodecs.LONG.encode(buf, val.fingerprint);
    }, buf -> {
        var dimension = ResourceKey.streamCodec(Registries.DIMENSION).decode(buf);
        var width = ByteBufCodecs.VAR_INT.decode(buf);
        var circumference = ByteBufCodecs.VAR_INT.decode(buf);
        var seed = ByteBufCodecs.LONG.decode(buf);
        var wallheight = ByteBufCodecs.VAR_INT.decode(buf);
        var surfaceReferenceY = ByteBufCodecs.VAR_INT.decode(buf);
        var terrainNoiseMapping = ByteBufCodecs.VAR_INT.decode(buf);
        var wallStyle = WALL_STYLE_CODEC.decode(buf);
        var skyProfile = SKY_PROFILE_CODEC.decode(buf);
        var generationSettings = GENERATION_SETTINGS_CODEC.decode(buf);
        var sunAngleOffsetY = ByteBufCodecs.FLOAT.decode(buf);
        var sunAngleOffsetZ = ByteBufCodecs.FLOAT.decode(buf);
        var formatVersion = ByteBufCodecs.VAR_INT.decode(buf);
        var fingerprint = ByteBufCodecs.LONG.decode(buf);
        return new RingSettingsPayload(dimension, width, circumference, seed, wallheight, surfaceReferenceY, terrainNoiseMapping, wallStyle, skyProfile, generationSettings, sunAngleOffsetY, sunAngleOffsetZ, formatVersion, fingerprint);
    });

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
