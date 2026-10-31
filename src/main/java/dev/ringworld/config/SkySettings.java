package dev.ringworld.config;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Function;

public record SkySettings(SkyType skyType, float sunWidth, boolean centeredSun, float sunAngleY, float sunAngleZ,
                          int sunTintColor) {
    public static final SkySettings DEFAULT = new SkySettings(SkyType.ATMOSPHERE, -1, false, 20, 20, 0);

    public enum SkyType {
        ATMOSPHERE(null, b -> b), NIGHT(new Vector3f(5 / 255f, 8 / 255f, 16 / 255f), b -> Math.max(b, 0.88F)), VOID(new Vector3f(1 / 255f, 1 / 255f, 3 / 255f), b -> 0F);

        private final Vector3fc skyColor;
        private final Function<Float, Float> starBrightness;

        SkyType(Vector3fc skyColor, Function<Float, Float> starBrightness) {
            this.skyColor = skyColor;
            this.starBrightness = starBrightness;
        }

        public Vector3fc getSkyColor(Vector3fc base) {
            return skyColor == null ? base : skyColor;
        }

        public float getStarBrightness(float base) {
            return starBrightness.apply(base);
        }
    }
}
