package dev.ringworld.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ringworld.api.RingWorldApi;
import dev.ringworld.config.RingWorldConfig;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSpawnBounds;
import dev.ringworld.world.RingWorldStorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.file.Path;

/**
 * Owns first-world spawn bounds and the read-only dimension-storage bridge.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements RingWorldStorageAccess {
    @Shadow
    @Final
    protected LevelStorageSource.LevelStorageAccess storageSource;

    @Override
    public Path ringworld$getDimensionPath(ResourceKey<Level> dimension) {
        return storageSource.getDimensionPath(dimension);
    }

    @Redirect(method = "setInitialSpawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getOrigin(Lnet/minecraft/world/level/levelgen/RandomState;)Lnet/minecraft/world/level/ChunkPos;"))
    private static ChunkPos ringworld$constrainInitialSpawn(ChunkGenerator generator, RandomState state, @Local(argsOnly = true, name = "level") ServerLevel level) {
        var vanilla = generator.getOrigin(state);
        RingWorldConfig config = RingWorldConfig.get(level);
        RingGeometry geometry = new RingGeometry(config.width(), config.circumference());
        return new ChunkPos(Math.floorMod(vanilla.x(), geometry.circumference()), Math.floorDiv(RingSpawnBounds.constrainInitialSpawnZ(vanilla.z() * 16 + 8, geometry), 16));
    }

    @Redirect(method = "setInitialSpawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/LevelData$RespawnData;of(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/BlockPos;FF)Lnet/minecraft/world/level/storage/LevelData$RespawnData;"), require = 3)
    private static LevelData.RespawnData ringworld$canonicalInitialRespawnData(ResourceKey<Level> dimension, BlockPos position, float yaw, float pitch, @Local(argsOnly = true, name = "level") ServerLevel level) {
        if (!RingWorldApi.isRingWorld(level)) {
            return LevelData.RespawnData.of(dimension, position, yaw, pitch);
        }
        RingWorldConfig config = RingWorldConfig.get(level);
        RingGeometry geometry = new RingGeometry(config.width(), config.circumference());
        return LevelData.RespawnData.of(dimension, RingSpawnBounds.canonicalInitialSpawn(position, geometry), yaw, pitch);
    }
}
