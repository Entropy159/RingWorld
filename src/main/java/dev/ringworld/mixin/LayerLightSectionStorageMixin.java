package dev.ringworld.mixin;

import dev.ringworld.api.RingWorldApi;
import dev.ringworld.server.RingLightSectionStorage;
import dev.ringworld.server.RingLightSectionTable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LayerLightSectionStorage.class)
public abstract class LayerLightSectionStorageMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/lighting/DataLayerStorageMap;copy()Lnet/minecraft/world/level/lighting/DataLayerStorageMap;"))
    private DataLayerStorageMap<?> ringworld$initializeTable(DataLayerStorageMap<?> map, LightLayer layer, LightChunkGetter chunks, DataLayerStorageMap<?> initial) {
        if (chunks.getLevel() instanceof ServerLevel level && RingWorldApi.isRingWorld(level)) {
            // Opt in before the first snapshot is published or any lighting tasks can run.
            ((RingLightSectionStorage) map).ringworld$table(new RingLightSectionTable<>());
        }
        return map.copy();
    }
}
