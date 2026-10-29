package dev.ringworld.world;

import dev.ringworld.Constants;
import dev.ringworld.RingWorldMod;
import dev.ringworld.world.atlas.RingAtlasFidelity;
import dev.ringworld.world.atlas.RingTerrainAtlas;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Bootstrap settings used only while creating a world. Once a world has saved
 * RingWorldSettings, its dimensions take precedence permanently.
 */
public record RingWorldConfig(int widthBlocks, int circumferenceBlocks, int wallHeightBlocks, RingWallStyle wallStyle,
                              RingSkyProfile skyProfile, RingWorldGenerationSettings generationSettings,
                              boolean pregenerateTerrainAtlas, boolean requestOceanMonument, float sunAngleOffsetY,
                              float sunAngleOffsetZ) {
    private static final Path configDirectory = FabricLoader.getInstance().getConfigDir().toAbsolutePath().normalize().resolve(RingWorldMod.MOD_ID);
    private static final Map<ResourceKey<Level>, RingWorldConfig> LOADED_CONFIGS = new HashMap<>();

    private static Path configPath(ResourceKey<Level> dimension) {
        Path path = configDirectory.resolve(dimension.identifier().getNamespace()).resolve(dimension.identifier().getPath() + ".properties");
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            RingWorldMod.LOGGER.error("Error creating config directories! ", e);
        }
        if (!path.toFile().exists()) {
            if (dimension != Level.OVERWORLD) {
                try {
                    Files.copy(configPath(Level.OVERWORLD), path);
                } catch (IOException e) {
                    RingWorldMod.LOGGER.error("Unable to copy ringworld properties for dimension " + dimension.identifier() + "! ", e);
                }
            }
            path = FabricLoader.getInstance().getConfigDir().resolve("ringworld.properties"); //Fall back to default settings
        }
        return path;
    }

    public static synchronized RingWorldConfig load(ResourceKey<Level> dimension) {
        var loaded = LOADED_CONFIGS.get(dimension);
        if (loaded != null) return loaded;
        Path path = configPath(dimension);
        Properties properties = new Properties();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not read " + path, exception);
            }
        } else {
            properties.setProperty("widthBlocks", Integer.toString(Constants.DEFAULT_WIDTH));
            properties.setProperty("circumferenceBlocks", Integer.toString(Constants.DEFAULT_CIRCUMFERENCE));
            properties.setProperty("wallHeightBlocks", Integer.toString(Constants.DEFAULT_WALL_HEIGHT));
            properties.setProperty("wallThicknessBlocks", Integer.toString(RingWallStyle.DEFAULT.thicknessBlocks()));
            properties.setProperty("wallPalette", Integer.toString(RingWallStyle.DEFAULT.palette().id()));
            properties.setProperty("wallPattern", Integer.toString(RingWallStyle.DEFAULT.pattern().id()));
            properties.setProperty("wallDecayPercent", Integer.toString(RingWallStyle.DEFAULT.decayPercent()));
            properties.setProperty("skyBackdrop", RingSkyProfile.Backdrop.ATMOSPHERE.name());
            properties.setProperty("sunStyle", RingSkyProfile.LightSource.SMALL.name());
            properties.setProperty("atlasFidelity", RingAtlasFidelity.BALANCED.name());
            properties.setProperty("worldLayout", RingWorldLayout.VANILLA.name());
            properties.setProperty("continuousRingRiver", "false");
            properties.setProperty("moreStructures", "false");
            properties.setProperty("pregenerateTerrainAtlas", "true");
            properties.setProperty("requestOceanMonument", "false");
            properties.setProperty("sunAngleOffsetY", "20");
            properties.setProperty("sunAngleOffsetZ", "20");
            try {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path)) {
                    properties.store(writer, "RingWorld bootstrap settings. Change before first world load only.");
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not create " + path, exception);
            }
        }
        int width = integer(properties, "widthBlocks", Constants.DEFAULT_WIDTH);
        int circumference = integer(properties, "circumferenceBlocks", Constants.DEFAULT_CIRCUMFERENCE);
        int wallHeight = integer(properties, "wallHeightBlocks", Constants.DEFAULT_WALL_HEIGHT);
        RingWallStyle wallStyle = wallStyle(properties);
        RingSkyProfile skyProfile = skyProfile(properties);
        RingWorldGenerationSettings generationSettings = generationSettings(properties);
        boolean pregenerateTerrainAtlas = Boolean.parseBoolean(properties.getProperty("pregenerateTerrainAtlas", "true"));
        boolean requestOceanMonument = Boolean.parseBoolean(properties.getProperty("requestOceanMonument", "false"));
        float sunAngleOffsetY = decimal(properties, "sunAngleOffsetY", 20);
        float sunAngleOffsetZ = decimal(properties, "sunAngleOffsetZ", 20);
        // Saved worlds ignore this bootstrap layout, so retain structural parsing
        // support for old configuration while first-world creation applies the
        // stricter new-world admission below.
        new RingGeometry(width, circumference);
        if (wallHeight < 32) throw new IllegalArgumentException("wallHeightBlocks must be at least 32");
        loaded = new RingWorldConfig(width, circumference, wallHeight, wallStyle, skyProfile, generationSettings, pregenerateTerrainAtlas, requestOceanMonument, sunAngleOffsetY, sunAngleOffsetZ);
        RingWorldMod.LOGGER.info("RingWorld bootstrap settings for {}: width={}, circumference={}, wallHeight={}, pregenerateTerrainAtlas={}, requestOceanMonument={}, sunAngleOffsetY={}, sunAngleOffsetZ={}", dimension.identifier(), width, circumference, wallHeight, pregenerateTerrainAtlas, requestOceanMonument, sunAngleOffsetY, sunAngleOffsetZ);
        LOADED_CONFIGS.put(dimension, loaded);
        return loaded;
    }

    public static synchronized RingWorldConfig saveBootstrapLayout(ResourceKey<Level> dimension, int widthBlocks, int circumferenceBlocks, int wallHeightBlocks, RingWallStyle wallStyle, RingSkyProfile skyProfile, RingWorldGenerationSettings generationSettings, boolean requestOceanMonument, float sunAngleOffsetY, float sunAngleOffsetZ) {
        if (wallStyle == null) throw new IllegalArgumentException("wall style is required");
        if (skyProfile == null) throw new IllegalArgumentException("sky profile is required");
        if (generationSettings == null) {
            throw new IllegalArgumentException("generation settings are required");
        }
        validateNewWorldLayout(widthBlocks, circumferenceBlocks, wallHeightBlocks, wallStyle.thicknessBlocks(), RingTerrainAtlas.SAMPLE_STEP_BLOCKS);
        boolean effectiveMonumentRequest = effectiveOceanMonumentRequest(new RingGeometry(widthBlocks, circumferenceBlocks), requestOceanMonument);
        RingWorldConfig current = load(dimension);
        RingWorldConfig replacement = new RingWorldConfig(widthBlocks, circumferenceBlocks, wallHeightBlocks, wallStyle, skyProfile, generationSettings, current.pregenerateTerrainAtlas(), effectiveMonumentRequest, sunAngleOffsetY, sunAngleOffsetZ);
        Properties properties = new Properties();
        properties.setProperty("widthBlocks", Integer.toString(widthBlocks));
        properties.setProperty("circumferenceBlocks", Integer.toString(circumferenceBlocks));
        properties.setProperty("wallHeightBlocks", Integer.toString(wallHeightBlocks));
        properties.setProperty("wallPreset", RingWallStyle.Preset.matching(wallStyle).name());
        properties.setProperty("wallThicknessBlocks", Integer.toString(wallStyle.thicknessBlocks()));
        properties.setProperty("wallPalette", Integer.toString(wallStyle.palette().id()));
        properties.setProperty("wallPattern", Integer.toString(wallStyle.pattern().id()));
        properties.setProperty("wallDecayPercent", Integer.toString(wallStyle.decayPercent()));
        properties.setProperty("wallStyleFormat", Integer.toString(wallStyle.formatVersion()));
        properties.setProperty("skyBackdrop", skyProfile.backdrop().name());
        properties.setProperty("sunStyle", skyProfile.lightSource().name());
        properties.setProperty("atlasFidelity", generationSettings.atlasFidelity().name());
        properties.setProperty("worldLayout", generationSettings.layout().name());
        properties.setProperty("continuousRingRiver", Boolean.toString(generationSettings.continuousRiver()));
        properties.setProperty("moreStructures", Boolean.toString(generationSettings.moreStructures()));
        properties.setProperty("pregenerateTerrainAtlas", Boolean.toString(current.pregenerateTerrainAtlas()));
        properties.setProperty("requestOceanMonument", Boolean.toString(effectiveMonumentRequest));
        properties.setProperty("sunAngleOffsetY", Float.toString(sunAngleOffsetY));
        properties.setProperty("sunAngleOffsetZ", Float.toString(sunAngleOffsetZ));
        Path path = configPath(dimension);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "RingWorld bootstrap settings. Saved world settings remain immutable.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not update " + path, exception);
        }
        LOADED_CONFIGS.put(dimension, replacement);
        if (requestOceanMonument && !effectiveMonumentRequest) {
            RingWorldMod.LOGGER.warn("Ignored ocean-monument request: width={} cannot fit its required margins", widthBlocks);
        }
        RingWorldMod.LOGGER.info("Updated first-world RingWorld layout: {}x{}, wallHeight={}, requestOceanMonument={}", circumferenceBlocks, widthBlocks, wallHeightBlocks, effectiveMonumentRequest);
        return replacement;
    }

    static void validateNewWorldLayout(int widthBlocks, int circumferenceBlocks, int wallHeightBlocks, int rimThicknessBlocks, int atlasSampleStepBlocks) {
        RingGeometry geometry = new RingGeometry(widthBlocks, circumferenceBlocks);
        if (circumferenceBlocks < Constants.MIN_CIRCUMFERENCE) {
            throw new IllegalArgumentException("circumferenceBlocks must be at least " + Constants.MIN_CIRCUMFERENCE + " for a new RingWorld");
        }
        RingDimensionReport.evaluate(geometry, wallHeightBlocks, Constants.DEFAULT_MIN_Y, Constants.DEFAULT_MAX_Y, rimThicknessBlocks, atlasSampleStepBlocks).requireValid();
    }

    /**
     * Authoritative new-world request gate shared by UI and server ownership paths.
     */
    static boolean effectiveOceanMonumentRequest(RingGeometry geometry, boolean requested) {
        return requested && RingMonumentPlacement.hasCandidateSpace(geometry);
    }

    private static int integer(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static float decimal(Properties properties, String key, float fallback) {
        try {
            return Float.parseFloat(properties.getProperty(key, Float.toString(fallback)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a decimal", e);
        }
    }

    private static RingWallStyle wallStyle(Properties properties) {
        if (properties.containsKey("wallThicknessBlocks")) {
            int format = integer(properties, "wallStyleFormat", RingWallStyle.FORMAT_VERSION);
            return new RingWallStyle(integer(properties, "wallThicknessBlocks", RingWallStyle.DEFAULT.thicknessBlocks()), RingWallStyle.Palette.fromId(integer(properties, "wallPalette", RingWallStyle.DEFAULT.palette().id())), RingWallStyle.Pattern.fromId(integer(properties, "wallPattern", RingWallStyle.DEFAULT.pattern().id())), integer(properties, "wallDecayPercent", RingWallStyle.DEFAULT.decayPercent()), format);
        }
        if (!properties.containsKey("wallPreset")) return RingWallStyle.DEFAULT;
        String presetName = properties.getProperty("wallPreset");
        try {
            return RingWallStyle.Preset.valueOf(presetName.trim()).style();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("wallPreset must name a supported rim preset", exception);
        }
    }

    private static RingSkyProfile skyProfile(Properties properties) {
        if (properties.containsKey("skyBackdrop") || properties.containsKey("sunStyle")) {
            try {
                RingSkyProfile.Backdrop backdrop = RingSkyProfile.Backdrop.valueOf(properties.getProperty("skyBackdrop", "ATMOSPHERE").trim().toUpperCase(java.util.Locale.ROOT));
                RingSkyProfile.LightSource source = RingSkyProfile.LightSource.valueOf(properties.getProperty("sunStyle", "SMALL").trim().toUpperCase(java.util.Locale.ROOT));
                return new RingSkyProfile(backdrop, source, RingSkyProfile.FORMAT_VERSION);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("skyBackdrop must be ATMOSPHERE, NIGHT, or VOID and sunStyle must be SMALL, LARGE, or NONE", exception);
            }
        }
        return legacySkyProfile(properties.getProperty("skyPreset", "MINECRAFT_ATMOSPHERE"));
    }

    private static RingWorldGenerationSettings generationSettings(Properties properties) {
        try {
            RingAtlasFidelity fidelity = RingAtlasFidelity.valueOf(properties.getProperty("atlasFidelity", "BALANCED").trim().toUpperCase(java.util.Locale.ROOT));
            RingWorldLayout layout = RingWorldLayout.valueOf(properties.getProperty("worldLayout", "VANILLA").trim().toUpperCase(java.util.Locale.ROOT));
            boolean river = Boolean.parseBoolean(properties.getProperty("continuousRingRiver", "false"));
            boolean structures = Boolean.parseBoolean(properties.getProperty("moreStructures", "false"));
            return new RingWorldGenerationSettings(fidelity, layout, river, structures, RingWorldGenerationSettings.FORMAT_VERSION);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("atlasFidelity or worldLayout names an unsupported generation preset", exception);
        }
    }

    /**
     * Reads the five combined development presets written before sky and sun became independent.
     */
    private static RingSkyProfile legacySkyProfile(String presetName) {
        try {
            return switch (presetName.trim().toUpperCase(java.util.Locale.ROOT)) {
                case "MINECRAFT_ATMOSPHERE" ->
                        new RingSkyProfile(RingSkyProfile.Backdrop.ATMOSPHERE, RingSkyProfile.LightSource.SMALL, RingSkyProfile.FORMAT_VERSION);
                case "SPACE_HABITAT" ->
                        new RingSkyProfile(RingSkyProfile.Backdrop.NIGHT, RingSkyProfile.LightSource.SMALL, RingSkyProfile.FORMAT_VERSION);
                case "DISTANT_STAR" ->
                        new RingSkyProfile(RingSkyProfile.Backdrop.NIGHT, RingSkyProfile.LightSource.LARGE, RingSkyProfile.FORMAT_VERSION);
                case "NIGHT_HABITAT" ->
                        new RingSkyProfile(RingSkyProfile.Backdrop.NIGHT, RingSkyProfile.LightSource.NONE, RingSkyProfile.FORMAT_VERSION);
                case "MINIMAL_VOID" ->
                        new RingSkyProfile(RingSkyProfile.Backdrop.VOID, RingSkyProfile.LightSource.NONE, RingSkyProfile.FORMAT_VERSION);
                default -> throw new IllegalArgumentException("unknown legacy sky preset");
            };
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("skyPreset must name a supported sky preset", exception);
        }
    }
}
