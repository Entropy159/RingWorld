package dev.ringworld.client.config;

import dev.ringworld.client.RingLodQuality;
import dev.ringworld.config.ConfigLoader;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingNearbyProjection;
import dev.ringworld.world.RingObjectTransform;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public record ClientRingWorldConfig(int distortionChunks, RingLodQuality lodQuality, boolean enableAtlas,
                                    int cloudHeightOffset, float cloudEdgeFade) {
    public static final ClientRingWorldConfig DEFAULT = new ClientRingWorldConfig(3, RingLodQuality.HIGH, true, 48, 16);

    private static ClientRingWorldConfig CURRENT = null;

    public static ClientRingWorldConfig get() {
        if (CURRENT != null) return CURRENT;
        var config = getRaw();
        CURRENT = config;
        return config;
    }

    private static Path getFilePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("ringworld-client.json");
    }

    public static ClientRingWorldConfig getRaw() {
        try {
            Path configPath = getFilePath();
            ClientRingWorldConfig config = DEFAULT;
            if (configPath.toFile().exists()) {
                String data = Files.readString(configPath, StandardCharsets.UTF_8);
                config = ConfigLoader.GSON.fromJson(data, ClientRingWorldConfig.class);
            }
            config.save();
            return config;
        } catch (IOException e) {
            throw new RuntimeException("Failed to get client config!", e);
        }
    }

    private void save() {
        try {
            Files.writeString(getFilePath(), ConfigLoader.GSON.toJson(this), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write client config!", e);
        }
    }

    public boolean distorionEnabled() {
        return distortionChunks > 0;
    }

    public void setDistortionChunks(int newValue) {
        var config = new ClientRingWorldConfig(newValue, lodQuality, enableAtlas, cloudHeightOffset, cloudEdgeFade);
        CURRENT = config;
        config.save();
    }

    public void setLodQuality(RingLodQuality newValue) {
        var config = new ClientRingWorldConfig(distortionChunks, newValue, enableAtlas, cloudHeightOffset, cloudEdgeFade);
        CURRENT = config;
        config.save();
    }

    public void setEnableAtlas(boolean newValue) {
        var config = new ClientRingWorldConfig(distortionChunks, lodQuality, newValue, cloudHeightOffset, cloudEdgeFade);
        CURRENT = config;
        config.save();
    }

    public void setCloudHeightOffset(int newValue) {
        var config = new ClientRingWorldConfig(distortionChunks, lodQuality, enableAtlas, newValue, cloudEdgeFade);
        CURRENT = config;
        config.save();
    }

    public void setCloudEdgeFade(float newValue) {
        var config = new ClientRingWorldConfig(distortionChunks, lodQuality, enableAtlas, cloudHeightOffset, newValue);
        CURRENT = config;
        config.save();
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
}
