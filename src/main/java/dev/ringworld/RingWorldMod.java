package dev.ringworld;

import dev.ringworld.api.RingCompatibilityContract;
import dev.ringworld.net.RingWorldNetworking;
import dev.ringworld.server.FabricRingWorldServer;
import dev.ringworld.world.RingWorldConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RingWorldMod implements ModInitializer {
    public static final String MOD_ID = "ringworld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        RingWorldConfig.configureDirectory(FabricLoader.getInstance().getConfigDir());
        logLoadedConflicts();
        RingWorldConfig.load();
        RingWorldNetworking.registerPayloads();
        FabricRingWorldServer.register();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void logLoadedConflicts() {
        var loadedIds = FabricLoader.getInstance().getAllMods().stream().map(container -> container.getMetadata().getId()).toList();
        for (RingCompatibilityContract.Conflict conflict : RingCompatibilityContract.findLoadedConflicts(loadedIds)) {
            RingWorldMod.LOGGER.error("Unsupported RingWorld combination detected: {} ({}): {}. " + "Remove it before reporting RingWorld rendering or topology defects.", conflict.displayName(), conflict.modId(), conflict.reason());
        }
    }
}
