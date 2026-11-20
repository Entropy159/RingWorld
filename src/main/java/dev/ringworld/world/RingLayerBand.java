package dev.ringworld.world;

public class RingLayerBand {
    public static final float ORIGINAL = 4f;
    public static final float REPLACEMENT = 8f;

    public static final float MIN_BAND = 0.5f;
    public static final float MAX_BAND = 2.0f;
    public static final float DEFAULT_BAND = 2.0f;
    private static float bandWidth = DEFAULT_BAND;

    public static float bandWidth() {
        return bandWidth;
    }

    public static void setBandWidth(float width) {
        if (!Float.isFinite(width) || width < MIN_BAND || width > MAX_BAND)
            throw new IllegalArgumentException("Layer band width out of range");
        bandWidth = width;
    }
}
