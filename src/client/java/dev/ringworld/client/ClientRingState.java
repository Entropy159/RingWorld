package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.config.RingWorldConfig;
import dev.ringworld.net.RingBuildingSettingsPayload;
import dev.ringworld.net.RingTerrainAtlasMetadataPayload;
import dev.ringworld.net.RingTerrainPreviewPayload;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingPosition;
import dev.ringworld.world.RingTerrainPreview;
import dev.ringworld.world.RingTerrainPreviewStage;
import dev.ringworld.world.atlas.RingAtlasCacheWriter;
import dev.ringworld.world.atlas.RingTerrainAtlas;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Client copy of the immutable settings received during login.
 */
public final class ClientRingState {
    private static final Map<ResourceKey<Level>, RingWorldConfig> CONFIGS = new HashMap<>();
    private static volatile boolean outsideBuilding = true;
    private static volatile long generatorSeed;
    private static volatile long layoutFingerprint;
    @Nullable
    private static volatile RingPosition cameraPosition;
    @Nullable
    private static volatile RingTerrainAtlas terrainAtlas;
    @Nullable
    private static volatile RingTerrainPreview terrainPreview;
    private static volatile int terrainPreviewStage = -1;
    private static volatile long serverAtlasWorldHash;
    private static volatile boolean hasServerAtlasWorldHash;
    @Nullable
    private static Path terrainAtlasCachePath;
    private static int terrainAtlasRevision;
    private static boolean terrainAtlasDirty;
    private static boolean terrainAtlasPendingRender;
    private static long lastTerrainAtlasSaveMillis;
    private static long lastTerrainAtlasPublishMillis;
    private static long terrainAtlasPendingSinceMillis;
    private static long lastTerrainAtlasChangeMillis;
    private static final Path cacheDirectory = FabricLoader.getInstance().getGameDir().resolve(RingWorldMod.MOD_ID + "-cache");
    private static final RingAtlasCacheWriter CACHE_WRITER = new RingAtlasCacheWriter();
    private static java.util.concurrent.CompletableFuture<Void> terrainAtlasSave;

    public static void set(ResourceKey<Level> dimension, RingWorldConfig config, long newGeneratorSeed, long newLayoutFingerprint) {
        CONFIGS.put(dimension, config);
        generatorSeed = newGeneratorSeed;
        layoutFingerprint = newLayoutFingerprint;
        cameraPosition = null;
        terrainAtlas = null;
        terrainPreview = null;
        terrainPreviewStage = -1;
        serverAtlasWorldHash = 0;
        hasServerAtlasWorldHash = false;
        terrainAtlasCachePath = null;
        terrainAtlasRevision++;
        terrainAtlasDirty = false;
        terrainAtlasPendingRender = false;
        terrainAtlasPendingSinceMillis = 0L;
        lastTerrainAtlasChangeMillis = 0L;
        lastTerrainAtlasSaveMillis = 0L;
        lastTerrainAtlasPublishMillis = 0L;
    }

    @Nullable
    public static RingGeometry geometry() {
        var config = config();
        return config == null ? null : config.geometry();
    }

    public static @Nullable RingWorldConfig config() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return null;
        }
        return config(client.level.dimension());
    }

    public static @Nullable RingWorldConfig config(ResourceKey<Level> dimension) {
        return CONFIGS.get(dimension);
    }

    public static boolean outsideBuilding() {
        return outsideBuilding;
    }

    public static void setBuildingSettings(RingBuildingSettingsPayload payload) {
        if (payload.fingerprint() != layoutFingerprint) return;
        outsideBuilding = payload.enabled();
    }

    public static long generatorSeed() {
        return generatorSeed;
    }

    public static long layoutFingerprint() {
        return layoutFingerprint;
    }

    public static void updateCameraPosition(double canonicalX) {
        RingGeometry currentGeometry = geometry();
        if (currentGeometry == null) return;
        RingPosition previous = cameraPosition;
        if (previous == null) {
            cameraPosition = RingPosition.fromPresentationX(canonicalX, currentGeometry);
            return;
        }
        double nearest = currentGeometry.nearestImageX(canonicalX, previous.presentationX(currentGeometry));
        cameraPosition = RingPosition.fromPresentationX(nearest, currentGeometry);
    }

    public static boolean installTerrainAtlas(RingTerrainAtlasMetadataPayload metadata) {
        RingGeometry current = geometry();
        if (current == null || metadata.tileSize() != RingTerrainAtlas.TILE_SIZE) return false;
        RingTerrainAtlas replacement = null;
        ResourceKey<Level> newDimension = metadata.dimension();
        Path cache = cacheDirectory.resolve("terrain-" + newDimension.identifier().toLanguageKey() + "-" + Long.toUnsignedString(metadata.worldHash(), 16) + ".rwat.gz");
        if (metadata.revision() < 0L) return false;
        if (Files.exists(cache)) {
            try {
                replacement = RingTerrainAtlas.load(cache, current, metadata.worldHash());
                if (replacement.revision() != metadata.revision()) replacement = null;
            } catch (IOException exception) {
                RingWorldMod.LOGGER.warn("Ignoring invalid client RingWorld terrain cache {}", cache, exception);
            }
        }
        if (replacement == null) {
            replacement = new RingTerrainAtlas(current, newDimension, metadata.worldHash(), metadata.sampleStep());
        }
        if (replacement.columns() != metadata.columns() || replacement.rows() != metadata.rows()) {
            RingWorldMod.LOGGER.warn("Server RingWorld terrain atlas dimensions do not match ring geometry");
            return false;
        }
        terrainAtlas = replacement;
        serverAtlasWorldHash = metadata.worldHash();
        hasServerAtlasWorldHash = true;
        terrainAtlasCachePath = cache;
        terrainAtlasSave = null;
        terrainAtlasRevision++;
        terrainAtlasDirty = false;
        terrainAtlasPendingRender = false;
        RingWorldMod.LOGGER.info("RingWorld terrain atlas ready: {}/{} cached cells ({}%)", replacement.presentCount(), replacement.cellCount(), Math.round(replacement.completion() * 1000.0) / 10.0);
        return replacement.isComplete() && replacement.revision() == metadata.revision();
    }

    public static void applyTerrainAtlasTile(long worldHash, int tileX, int tileZ, byte[] data) {
        RingTerrainAtlas atlas = terrainAtlas;
        if (atlas == null || atlas.worldHash() != worldHash) return;
        try {
            boolean wasComplete = atlas.isComplete();
            if (!atlas.applyTile(tileX, tileZ, data)) return;
            boolean becameComplete = !wasComplete && atlas.isComplete();
            terrainAtlasDirty = true;
            long now = System.currentTimeMillis();
            if (!terrainAtlasPendingRender) terrainAtlasPendingSinceMillis = now;
            terrainAtlasPendingRender = true;
            lastTerrainAtlasChangeMillis = now;
            // Force the first complete surface immediately. Later updates to
            // an already-complete atlas use the normal coalescing windows so
            // a dirty-tile burst cannot rebuild the full texture and mesh for
            // every packet.
            publishTerrainAtlasIfDue(becameComplete);
            saveTerrainAtlasIfDue(becameComplete);
            if (becameComplete) {
                RingWorldMod.LOGGER.info("RingWorld terrain atlas download complete: {} cells", atlas.presentCount());
            }
        } catch (IOException | IndexOutOfBoundsException exception) {
            RingWorldMod.LOGGER.warn("Rejected invalid RingWorld terrain atlas tile {},{}", tileX, tileZ, exception);
        }
    }

    /**
     * Accepts an ordered server tile batch and schedules its cache checkpoint.
     */
    public static void commitTerrainAtlasRevision(long worldHash, long revision) {
        RingTerrainAtlas atlas = terrainAtlas;
        if (atlas == null || atlas.worldHash() != worldHash) return;
        try {
            if (!atlas.commitRevision(revision)) return;
            terrainAtlasDirty = true;
            // Changed tiles already request a visual publication. A revision
            // commit is a durable transaction marker, not a texture change;
            // forcing another render generation here rebuilt an identical
            // complete-ring texture after every tile batch.
            saveTerrainAtlasCacheIfDue(false);
            RingWorldMod.LOGGER.info("RingWorld terrain atlas revision {} committed", revision);
        } catch (IOException exception) {
            RingWorldMod.LOGGER.warn("Rejected invalid RingWorld terrain atlas revision {}", revision, exception);
        }
    }

    @Nullable
    public static RingTerrainAtlas terrainAtlas() {
        return terrainAtlas;
    }

    @Nullable
    public static RingTerrainPreview terrainPreview() {
        return terrainPreview;
    }

    public static int terrainPreviewStage() {
        return terrainPreviewStage;
    }

    public static void installTerrainPreview(RingTerrainPreviewPayload payload) {
        RingTerrainAtlas atlas = terrainAtlas;
        if (atlas == null || atlas.worldHash() != payload.worldHash()) return;
        try {
            RingTerrainPreviewStage stage = RingTerrainPreviewStage.fromWireValue(payload.stage());
            RingTerrainPreview decoded = RingTerrainPreview.decode(payload.data());
            if (decoded.worldHash() != payload.worldHash()) {
                RingWorldMod.LOGGER.warn("Rejected RingWorld terrain preview with mismatched world identity");
                return;
            }
            terrainPreview = decoded;
            terrainPreviewStage = stage.wireValue();
            terrainAtlasRevision++;
            RingWorldMod.LOGGER.info("RingWorld {} seed preview ready: {}x{} ({} compressed bytes)", stage.logLabel(), decoded.columns(), decoded.rows(), payload.data().length);
        } catch (IOException | RuntimeException exception) {
            RingWorldMod.LOGGER.warn("Rejected invalid RingWorld terrain preview", exception);
        }
    }

    public static int terrainAtlasRevision() {
        return terrainAtlasRevision;
    }

    public static long terrainAtlasDurableRevision() {
        RingTerrainAtlas atlas = terrainAtlas;
        return atlas == null ? 0L : atlas.revision();
    }

    public static long serverAtlasWorldHash() {
        return serverAtlasWorldHash;
    }

    public static boolean hasServerAtlasWorldHash() {
        return hasServerAtlasWorldHash;
    }

    /**
     * Coalesces partial-cache writes while saving a newly completed atlas immediately.
     */
    public static void saveTerrainAtlasIfDue(boolean force) {
        publishTerrainAtlasIfDue(force);
        saveTerrainAtlasCacheIfDue(force);
    }

    private static void saveTerrainAtlasCacheIfDue(boolean force) {
        if (terrainAtlasSave != null && terrainAtlasSave.isCompletedExceptionally()) {
            terrainAtlasDirty = true;
            terrainAtlasSave = null;
        }
        RingTerrainAtlas atlas = terrainAtlas;
        Path cache = terrainAtlasCachePath;
        if (!terrainAtlasDirty || atlas == null || cache == null) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastTerrainAtlasSaveMillis < 10000L) return;
        // A complete Atlas is already available from the server. Wait until
        // active tile changes settle before copying the whole client cache;
        // the forced disconnect save still captures the latest state.
        if (!force && atlas.isComplete() && now - lastTerrainAtlasChangeMillis < 10000L) return;
        long snapshotStarted = System.nanoTime();
        terrainAtlasSave = CACHE_WRITER.submit(cache, atlas);
        long snapshotNanos = System.nanoTime() - snapshotStarted;
        if (snapshotNanos >= 16000000L)
            RingWorldMod.LOGGER.warn("RingWorld render stall candidate: cache snapshot/submission took {} ms", snapshotNanos / 1000000.0);
        terrainAtlasSave.whenComplete((ignored, exception) -> {
            if (exception != null)
                RingWorldMod.LOGGER.error("Could not save client RingWorld terrain atlas " + cache, exception);
        });
        terrainAtlasDirty = false;
        lastTerrainAtlasSaveMillis = now;
    }

    /**
     * Avoids rebuilding the complete-ring texture once per incoming network tile.
     */
    private static void publishTerrainAtlasIfDue(boolean force) {
        if (!terrainAtlasPendingRender) return;
        long now = System.currentTimeMillis();
        if (!force) {
            RingTerrainAtlas atlas = terrainAtlas;
            if (atlas != null && atlas.isComplete()) {
                // Distant LOD may trail authoritative block state briefly.
                // Batch natural leaf/fluid/terrain settling into one upload
                // after three quiet seconds, while bounding continuous churn
                // to a ten-second maximum delay.
                boolean quiet = now - lastTerrainAtlasChangeMillis >= 3000L;
                boolean maximumDelayReached = now - terrainAtlasPendingSinceMillis >= 10000L;
                if (!quiet && !maximumDelayReached) return;
            } else if (now - lastTerrainAtlasPublishMillis < 1000L) {
                return;
            }
        }
        terrainAtlasRevision++;
        terrainAtlasPendingRender = false;
        lastTerrainAtlasPublishMillis = now;
        terrainAtlasPendingSinceMillis = 0L;
    }

    public static void clear() {
        saveTerrainAtlasIfDue(true);
        CONFIGS.clear();
        outsideBuilding = true;
        layoutFingerprint = 0;
        generatorSeed = 0;
        cameraPosition = null;
        terrainAtlas = null;
        terrainPreview = null;
        terrainPreviewStage = -1;
        serverAtlasWorldHash = 0;
        hasServerAtlasWorldHash = false;
        terrainAtlasCachePath = null;
        // Queued immutable snapshots retain their own paths and finish in order.
        // Their completions must never mark a later session dirty.
        terrainAtlasSave = null;
        terrainAtlasDirty = false;
        terrainAtlasPendingRender = false;
        lastTerrainAtlasSaveMillis = 0;
        lastTerrainAtlasPublishMillis = 0;
        terrainAtlasPendingSinceMillis = 0;
        lastTerrainAtlasChangeMillis = 0;
        terrainAtlasRevision++;
    }
}
