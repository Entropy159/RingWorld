package dev.ringworld.net;

import dev.ringworld.Constants;
import dev.ringworld.world.RingLayoutFingerprint;
import dev.ringworld.world.RingSkyProfile;
import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Loader-neutral immutable-layout handshake derivation and comparison.
 */
public final class RingSettingsHandshake {
    public static RingSettingsPayload payloadFor(ResourceKey<Level> dimension, RingWorldSettings settings) {
        return payloadFor(dimension, settings, Constants.DEFAULT_SKY_PROFILE);
    }

    public static RingSettingsPayload payloadFor(ResourceKey<Level> dimension, RingWorldSettings settings, RingSkyProfile skyProfile) {
        return new RingSettingsPayload(dimension, settings.widthBlocks(), settings.circumferenceBlocks(), settings.generatorSeed(), settings.wallHeightBlocks(), settings.surfaceReferenceY(), settings.terrainNoiseMapping(), settings.wallStyle(), skyProfile, settings.generationSettings(), settings.sunAngleOffsetY(), settings.sunAngleOffsetZ(), settings.formatVersion(), settings.layoutFingerprint());
    }

    /**
     * Recomputes the identity from every settings field carried on the wire.
     */
    public static long fingerprintFor(RingSettingsPayload payload) {
        RingTerrainNoiseMapping.requireSupported(payload.terrainNoiseMapping());
        return RingLayoutFingerprint.compute(payload.width(), payload.circumference(), payload.seed(), payload.wallHeight(), payload.surfaceReferenceY(), payload.terrainNoiseMapping(), payload.wallStyle(), payload.generationSettings(), payload.formatVersion());
    }

    public static boolean hasMatchingPayloadFingerprint(RingSettingsPayload payload) {
        try {
            return fingerprintFor(payload) == payload.fingerprint();
        } catch (IllegalArgumentException invalidMapping) {
            return false;
        }
    }

    public static RingSettingsAckPayload acknowledgementFor(RingSettingsPayload payload) {
        return new RingSettingsAckPayload(payload.formatVersion(), fingerprintFor(payload));
    }

    public static boolean accepts(RingWorldSettings settings, RingSettingsAckPayload acknowledgement) {
        return acknowledgement.formatVersion() == settings.formatVersion() && acknowledgement.fingerprint() == settings.layoutFingerprint();
    }
}
