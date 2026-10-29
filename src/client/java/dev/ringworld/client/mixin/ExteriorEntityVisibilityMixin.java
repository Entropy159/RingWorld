package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Exterior void has no compiled terrain section; it must not hide entity models.
 */
@Mixin(LevelExtractor.class)
public abstract class ExteriorEntityVisibilityMixin {
    @Redirect(method = "isEntityVisible", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiledAndVisible(Lnet/minecraft/core/BlockPos;J)Z"))
    private boolean ringworld$visibleOutsideTerrain(LevelRenderer renderer, BlockPos pos, long frame) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry != null && (pos.getZ() < geometry.minWidthZ() || pos.getZ() > geometry.maxWidthZ())) return true;
        return renderer.isSectionCompiledAndVisible(pos, frame);
    }
}
