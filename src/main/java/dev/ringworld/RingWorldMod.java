package dev.ringworld;

import dev.ringworld.net.RingWorldNetworking;
import dev.ringworld.server.FabricRingWorldServer;
import dev.ringworld.world.RingWorldConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-neutral identity and logging shared by every platform adapter.
 */
public final class RingWorldMod implements ModInitializer {
    public static final String MOD_ID = "ringworld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        RingWorldConfig.configureDirectory(FabricLoader.getInstance().getConfigDir());
        FabricCompatibilityProbe.logLoadedConflicts();
        RingWorldConfig.load();
        RingWorldNetworking.registerPayloads();
        FabricRingWorldServer.register();
    }
}
