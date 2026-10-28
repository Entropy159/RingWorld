package dev.ringworld.server.atlas.pregen;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Fabric lifecycle bridge for the loader-neutral headless prewarm coordinator.
 */
public final class RingWorldHeadlessPrewarm {
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(HeadlessPrewarmCoordinator::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(HeadlessPrewarmCoordinator::serverStopping);
    }

    public static boolean requested(MinecraftServer server) {
        return HeadlessPrewarmCoordinator.requested(server);
    }

    public static boolean suppressesBackgroundAutostart(MinecraftServer server) {
        return HeadlessPrewarmCoordinator.suppressesBackgroundAutostart(server);
    }

    public static boolean rejectPlayerJoins(MinecraftServer server) {
        return HeadlessPrewarmCoordinator.rejectPlayerJoins(server);
    }

    public static void start(ServerLevel world) {
        HeadlessPrewarmCoordinator.start(world);
    }

    public static void failStartup(MinecraftServer server, ServerLevel world, Throwable failure) {
        HeadlessPrewarmCoordinator.failStartup(server, world, failure);
    }
}
