package dev.ringworld.api;

import dev.ringworld.Constants;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingBuildingSettings;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class RingWorldApi {
    public static boolean isRingWorld(Level world) {
        return world.dimensionTypeRegistration().is(Constants.RING_DIMENSION_TAG);
    }

    @Nullable
    public static RingWorldSettings settings(ServerLevel world) {
        return isRingWorld(world) ? RingWorldSettings.get(world) : null;
    }

    public static RingGeometry geometry(ServerLevel world) {
        RingWorldSettings settings = settings(world);
        if (settings == null) {
            throw new IllegalArgumentException("World is not a RingWorld dimension");
        }
        return settings.geometry();
    }

    /**
     * Converts any intrinsic/presentation X into the one authoritative storage plane.
     */
    public static Vec3 canonicalPosition(ServerLevel world, Vec3 intrinsicPosition) {
        RingGeometry geometry = geometry(world);
        return new Vec3(geometry.wrapX(intrinsicPosition.x), intrinsicPosition.y, intrinsicPosition.z);
    }

    /**
     * Selects the periodic image of a canonical position nearest one presentation X.
     */
    public static Vec3 nearestPresentationPosition(ServerLevel world, Vec3 canonicalPosition, double referencePresentationX) {
        RingGeometry geometry = geometry(world);
        return new Vec3(geometry.nearestImageX(canonicalPosition.x, referencePresentationX), canonicalPosition.y, canonicalPosition.z);
    }

    /**
     * Embeds an intrinsic position in the global cylindrical render space.
     */
    public static Vec3 physicalPosition(ServerLevel world, Vec3 intrinsicPosition) {
        return geometry(world).toPhysical(intrinsicPosition.x, intrinsicPosition.y, intrinsicPosition.z);
    }

    /**
     * Embeds position and vanilla yaw/pitch into one immutable physical-ring pose.
     */
    public static RingPhysicalPose physicalPose(ServerLevel world, Vec3 intrinsicPosition, float yawDegrees, float pitchDegrees) {
        return RingPhysicalPose.fromIntrinsic(geometry(world), intrinsicPosition, yawDegrees, pitchDegrees);
    }

    public static boolean allowPlacing(boolean enabled, RingGeometry geometry, BlockPlaceContext context) {
        return RingBuildingSettings.allows(enabled, geometry, context.getClickedPos().getZ()) && (!(context.getItemInHand().getItem() instanceof BlockItem item && item.getBlock() instanceof BedBlock) || RingBuildingSettings.allows(enabled, geometry, context.getClickedPos().relative(context.getHorizontalDirection()).getZ()));
    }

    public static int wrapChunkX(int chunkX, LevelAccessor level) {
        if (!(level instanceof ServerLevel serverWorld) || !RingWorldApi.isRingWorld(serverWorld)) {
            return chunkX;
        }
        RingGeometry geometry = RingWorldServer.geometryFor(serverWorld);
        return RingChunkCoordinates.wrapChunkX(chunkX, geometry);
    }
}
