package dev.ringworld.world;

import dev.ringworld.config.RingWorldConfig;
import dev.ringworld.config.WallSettings;

/**
 * Stable identity for every immutable value that changes canonical terrain or
 * the physical cylinder. Both sides of the settings handshake compute this
 * value independently.
 */
public final class RingLayoutFingerprint {
    private static final int FINGERPRINT_VERSION = 4;

    public static long compute(RingWorldConfig settings, long seed) {
        return compute(settings.width(), settings.circumference(), seed, settings.wallSettings().height(), settings.wallSettings());
    }

    public static long compute(int widthBlocks, int circumferenceBlocks, long generatorSeed, int wallHeightBlocks, WallSettings wallStyle) {
        long value = 0x9E3779B97F4A7C15L ^ generatorSeed;
        value = mix(value ^ FINGERPRINT_VERSION);
        value = mix(value ^ Integer.toUnsignedLong(widthBlocks));
        value = mix(value ^ (Integer.toUnsignedLong(circumferenceBlocks) << 1));
        value = mix(value ^ (Integer.toUnsignedLong(wallHeightBlocks) << 17));
        value = mix(value ^ (Integer.toUnsignedLong(wallStyle.thickness()) << 9));
        value = mix(value ^ (Integer.toUnsignedLong(wallStyle.palette().ordinal()) << 13));
        value = mix(value ^ (Integer.toUnsignedLong(wallStyle.pattern().ordinal()) << 21));
        value = mix(value ^ (Integer.toUnsignedLong(wallStyle.decay()) << 29));
        return mix(value ^ (Integer.toUnsignedLong(RingGenerationBoundary.RIM_STYLE_VERSION) << 25));
    }

    public static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
