package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.config.SkySettings;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps RingWorld sky and atmospheric fog continuous at every height.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void ringworld$matchSkyFog(Camera camera, int renderDistance, DeltaTracker tickCounter, float darkenWorldAmount, ClientLevel world, CallbackInfoReturnable<FogData> cir) {
        var config = ClientRingState.config();
        if (config == null || ClientRingState.geometry() == null || camera.getFluidInCamera() != FogType.NONE) return;
        if (camera.entity() instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS)))
            return;
        SkySettings.SkyType backdrop = config.skySettings().skyType();
        var color = backdrop.getSkyColor(camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, tickCounter.getGameTimeDeltaPartialTick(false)));
        cir.getReturnValue().color.set(color.x(), color.y(), color.z(), 1);
    }
}
