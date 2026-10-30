package dev.ringworld.config;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingLayoutFingerprint;
import net.minecraft.server.level.ServerLevel;

public record RingWorldConfig(int width, int circumference, WallSettings wallSettings, SkySettings skySettings,
                              boolean pregenAtlas) {
    public RingWorldConfig {
        new RingGeometry(width, circumference); //Error checking
    }

    public RingWorldConfig() {
        this(256, 16384, WallSettings.DEFAULT, SkySettings.DEFAULT, true);
    }

    public RingGeometry geometry() {
        return new RingGeometry(width, circumference);
    }

    public static RingWorldConfig get(ServerLevel world) {
        return SavedConfig.get(world).getConfig();
    }

    public long layoutFingerprint(long seed) {
        return RingLayoutFingerprint.compute(this, seed);
    }
}
