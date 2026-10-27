package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.RingAtlasHudRenderer;
import dev.ringworld.client.RingMinecraftClientAccess;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shows unobtrusive progress while the current world's terrain Atlas fills.
 */
@Mixin(Hud.class)
public abstract class GuiMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void ringworld$extractAtlasProgress(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        if (RingMinecraftClientAccess.hideGui(minecraft) || minecraft.level == null || ClientRingState.geometry() == null)
            return;
        RingAtlasHudRenderer.render(graphics, minecraft);
    }
}
