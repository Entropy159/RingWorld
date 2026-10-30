package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.api.RingWorldApi;
import dev.ringworld.config.RingWorldConfig;
import dev.ringworld.config.WallSettings;
import dev.ringworld.world.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Server-authoritative seam and boundary behaviour.
 */
public final class RingWorldServer {
    private static final Map<ServerLevel, RingGeometry> WORLD_GEOMETRY = new IdentityHashMap<>();
    private static final Map<ServerLevel, LinkedHashMap<Long, LevelChunk>> PENDING_LEGACY_RIM_MIGRATIONS = new IdentityHashMap<>();

    /**
     * Queues safe deferred rim migration from a loader-owned chunk-load callback.
     */
    public static void onChunkLoaded(ServerLevel world, LevelChunk chunk) {
        if (!RingWorldApi.isRingWorld(world)) return;
        if (RingGenerationBoundary.containsRim(chunk, geometryFor(world))) {
            PENDING_LEGACY_RIM_MIGRATIONS.computeIfAbsent(world, _ -> new LinkedHashMap<>()).putIfAbsent(chunk.getPos().pack(), chunk);
        }
    }

    /**
     * Clears loader-neutral state after a platform level-unload callback.
     */
    public static void onLevelUnloaded(ServerLevel world) {
        WORLD_GEOMETRY.remove(world);
        PENDING_LEGACY_RIM_MIGRATIONS.remove(world);
        if (RingWorldApi.isRingWorld(world)) RingNoiseCoordinates.clearCache();
    }

    /**
     * Loader-owned join callbacks call this after their own admission policy.
     */
    public static void onPlayerJoined(ServerPlayer player) {
        player.level().getServer().execute(() -> rescueEmbeddedPlayer(player));
    }

    public static RingGeometry attachWorldGeometry(ServerLevel world) {
        if (!RingWorldApi.isRingWorld(world)) return null;
        RingWorldConfig settings = RingWorldConfig.get(world);
        RingGeometry geometry = settings.geometry();
        WORLD_GEOMETRY.put(world, geometry);
        attachGeneratorSettings(world, geometry, settings.wallSettings());
        return geometry;
    }

    private static void attachGeneratorSettings(ServerLevel world, RingGeometry geometry, WallSettings wallStyle) {
        RingStructurePolicy policy = RingStructurePolicy.get(world);
        boolean guaranteeStronghold = policy.guaranteesStronghold();
        ChunkGenerator generator = world.getChunkSource().getGenerator();
        Climate.Sampler periodicClimateSampler = null;
        java.util.function.IntBinaryOperator oceanFloorHeight = null;
        var generatorState = world.getChunkSource().getGeneratorState();
        if (generator instanceof RingWorldGeneratorAccess access) {
            access.ringworld$setGeometry(geometry);
            access.ringworld$setWallHeight(wallStyle.height());
            access.ringworld$setWallStyle(wallStyle);
            access.ringworld$setGuaranteeStronghold(guaranteeStronghold);
            periodicClimateSampler = access.ringworld$getPeriodicClimateSampler(generatorState.randomState());
            oceanFloorHeight = (x, z) -> generator.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, world, generatorState.randomState());
        }
        if (generatorState instanceof RingStructureStateAccess access) {
            access.ringworld$setStructurePolicy(geometry, policy, generator.getSeaLevel(), periodicClimateSampler, oceanFloorHeight);
            if (policy.oceanMonument().status() == RingMonumentResolution.Status.PENDING) {
                RingMonumentResolution resolution = access.ringworld$resolvePendingOceanMonument();
                policy = RingStructurePolicy.resolvePendingOceanMonument(world, resolution);
                // New-world PENDING must become durable before any structure-start work can observe it.
                world.getDataStorage().saveAndJoin();
                access.ringworld$setStructurePolicy(geometry, policy, generator.getSeaLevel(), periodicClimateSampler, oceanFloorHeight);
                if (resolution.status() == RingMonumentResolution.Status.SATISFIED) {
                    RingWorldMod.LOGGER.info("Resolved requested RingWorld ocean monument: status={}, candidate={}", resolution.status(), resolution.candidate());
                } else {
                    RingWorldMod.LOGGER.warn("RingWorld ocean monument request could not be satisfied: status={}, reason={}", resolution.status(), resolution.reason());
                }
            } else if (!access.ringworld$hasCompatibleSavedOceanMonument()) {
                RingWorldMod.LOGGER.error("Saved RingWorld ocean-monument candidate is incompatible with the current built-in registry or biome data; it will not be moved or regenerated");
            }
        }
        RingWorldMod.LOGGER.debug("Attached RingWorld generator policy: guaranteeStronghold={}, monumentStatus={}", guaranteeStronghold, policy.oceanMonument().status());
    }

    /**
     * Allocation-free geometry lookup for chunk and network hot paths.
     */
    public static RingGeometry geometryFor(ServerLevel world) {
        RingGeometry geometry = WORLD_GEOMETRY.get(world);
        return geometry != null ? geometry : attachWorldGeometry(world);
    }

    public static void tickRingWorld(ServerLevel world) {
        if (!RingWorldApi.isRingWorld(world)) return;
        RingGeometry geometry = geometryFor(world);
        // The server owns exactly one circumference plane. Clients may keep a
        // nearby presentation image for smooth seam rendering, but no entity
        // position outside [0, C) survives the authoritative tick boundary.
        for (Entity entity : world.getAllEntities()) {
            canonicalizeEntityPosition(entity, geometry);
        }
        migrateOneLegacyRimChunk(world, geometry);
    }

    /**
     * Folds one live server entity onto the single authoritative ring plane
     * without teleport packets, velocity changes, or camera rotation.
     *
     * @return the X shift applied to the entity
     */
    public static double canonicalizeEntityPosition(Entity entity, RingGeometry geometry) {
        if (!(entity.level() instanceof ServerLevel world) || !RingWorldApi.isRingWorld(world)) return 0.0;
        double sourceX = entity.getX();
        double canonicalX = geometry.wrapX(sourceX);
        if (canonicalX == sourceX) return 0.0;
        entity.setPos(canonicalX, entity.getY(), entity.getZ());
        double shift = canonicalX - sourceX;
        if (entity instanceof RingEntityFoldAccess access) {
            access.ringworld$onCanonicalFold(shift);
        }
        if (entity instanceof Mob mob && mob.getNavigation() instanceof RingNavigationAccess navigation) {
            navigation.ringworld$foldPath((int) Math.rint(shift));
        }
        return shift;
    }

    /**
     * Migrates at most one loaded boundary chunk per tick. Besides avoiding
     * chunk-load reentrancy, the limit keeps an old world's first login from
     * turning hundreds of rim conversions into one watchdog-sized tick.
     */
    private static void migrateOneLegacyRimChunk(ServerLevel world, RingGeometry geometry) {
        LinkedHashMap<Long, net.minecraft.world.level.chunk.LevelChunk> pending = PENDING_LEGACY_RIM_MIGRATIONS.get(world);
        if (pending == null || pending.isEmpty()) return;

        var iterator = pending.entrySet().iterator();
        var entry = iterator.next();
        iterator.remove();

        long started = System.nanoTime();
        boolean migrated = RingGenerationBoundary.migrateLegacyRim(entry.getValue(), geometry, RingWorldConfig.get(world).wallSettings().height());
        if (migrated) {
            double elapsedMs = (System.nanoTime() - started) / 1000000.0;
            RingWorldMod.LOGGER.info("Migrated legacy rim chunk {} in {} ms ({} queued)", entry.getValue().getPos(), elapsedMs, pending.size());
        }
        if (pending.isEmpty()) PENDING_LEGACY_RIM_MIGRATIONS.remove(world);
    }

    /**
     * A player saved while a seam-adjacent chunk was still settling can load
     * with their bounding box inside newly available terrain. Vanilla then
     * renders an apparently black world because the camera is enclosed by a
     * solid block. Validate the join pose once, and only move players whose
     * actual collision box is obstructed.
     */
    public static void rescueEmbeddedPlayer(ServerPlayer player) {
        ServerLevel world = player.level();
        if (!RingWorldApi.isRingWorld(world)) return;
        if (world.noCollision(player)) return;

        int blockX = (int) Math.floor(player.getX());
        int blockZ = (int) Math.floor(player.getZ());
        int surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        double targetY = Math.max(surfaceY, Math.floor(player.getY()) + 1.0);

        while (targetY < world.getMaxY() && !world.noCollision(player, player.getBoundingBox().move(0.0, targetY - player.getY(), 0.0))) {
            targetY += 1.0;
        }
        if (targetY >= world.getMaxY()) {
            RingWorldMod.LOGGER.warn("Could not find an unobstructed join pose for {} at {}, {}, {}", player.getName().getString(), player.getX(), player.getY(), player.getZ());
            return;
        }

        double sourceY = player.getY();
        player.teleportTo(world, player.getX(), targetY, player.getZ(), Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
        player.setDeltaMovement(Vec3.ZERO);
        RingWorldMod.LOGGER.warn("Rescued embedded player {} from y={} to y={} at x={}, z={}", player.getName().getString(), sourceY, targetY, player.getX(), player.getZ());
    }
}
