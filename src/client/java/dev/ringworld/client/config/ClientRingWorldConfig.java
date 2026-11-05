package dev.ringworld.client.config;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.ringworld.config.ConfigLoader;
import dev.ringworld.world.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public record ClientRingWorldConfig(int distortionChunks, RingLodQuality lodQuality) {
    public static final ClientRingWorldConfig DEFAULT = new ClientRingWorldConfig(3, RingLodQuality.HIGH);

    private static ClientRingWorldConfig CURRENT = null;

    public static ClientRingWorldConfig get() {
        if (CURRENT != null) return CURRENT;
        var config = getRaw();
        CURRENT = config;
        return config;
    }

    public static ClientRingWorldConfig getRaw() {
        try {
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve("ringworld-client.json");
            ClientRingWorldConfig config = DEFAULT;
            if (configPath.toFile().exists()) {
                String data = Files.readString(configPath, StandardCharsets.UTF_8);
                config = ConfigLoader.GSON.fromJson(data, ClientRingWorldConfig.class);
            }
            Files.writeString(configPath, ConfigLoader.GSON.toJson(config), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return config;
        } catch (IOException e) {
            throw new RuntimeException("Failed to get client config!", e);
        }
    }

    public boolean distorionEnabled() {
        return distortionChunks > 0;
    }

    public RingNearbyProjection projection(RingGeometry geometry, Vec3 camera) {
        Minecraft client = Minecraft.getInstance();
        double top = client.level == null ? 320.0 : client.level.getMinY() + client.level.getHeight();
        return RingNearbyProjection.create(geometry, camera.y, top, distortionChunks);
    }

    public RingObjectTransform distortionTransform(RingGeometry geometry, Vec3 camera, double x, double y, double z) {
        if (!distorionEnabled()) {
            return RingObjectTransform.fromCameraRelative(geometry, camera, x, y, z);
        }
        return projection(geometry, camera).object(camera, x, y, z);
    }

    public RingRenderProfile renderProfile(RingGeometry geometry, double distance) {
        return lodQuality.profile(geometry, distance, RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize());
    }
}
