package dev.ringworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ringworld.Constants;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.RingDistortionTuning;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSkyCycle;
import dev.ringworld.world.RingSkyProfile;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixed toned sun plus the active texture-backed complete-ring surface.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRenderingMixin {
    @Invoker("renderSun")
    protected abstract void ringworld$invokeRenderSun(float alpha, PoseStack matrices);

    @Unique
    private float ringworld$cameraY;
    @Unique
    private float ringworld$cameraZ;
    @Unique
    private double ringworld$cameraX;
    @Unique
    private float ringworld$starTiltRadians;
    @Unique
    private boolean ringworld$renderingCenteredSun;
    @Unique
    private RingSkyCycle.SunVisual ringworld$sunVisual = RingSkyCycle.sunVisual(6000.0);

    @Inject(method = "close", at = @At("TAIL"))
    private void ringworld$closeSkyGeometry(CallbackInfo ci) {
        RingSurfaceTextureRenderer.clear();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void ringworld$updateFixedSky(ClientLevel world, float tickProgress, Camera camera, SkyRenderState state, CallbackInfo ci) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;
        if (notVanilla()) {
            state.sunAngle = RingSkyCycle.FIXED_SUN_ANGLE_RADIANS;
            state.moonAngle = RingSkyCycle.FIXED_SUN_ANGLE_RADIANS;
            // Stars are infinitely distant but directionally fixed in physical
            // ring space. Counter-rotate the player's local tangent frame so a
            // cluster below one side appears overhead on the opposite side.
            state.sunriseAndSunsetColor = 0;
        }
        state.starAngle = RingSkyCycle.starFieldAngleRadians(geometry, camera.position().x);
        RingSkyProfile skyProfile = ClientRingState.skyProfile();
        switch (skyProfile.backdrop()) {
            case ATMOSPHERE -> {
            }
            case NIGHT -> {
                state.skyColor = 0x050810;
                state.starBrightness = Math.max(state.starBrightness, 0.88F);
            }
            case VOID -> {
                state.skyColor = 0x010103;
                state.starBrightness = 0.0F;
            }
        }
        // FogRenderer clears to the live backdrop. Flat sky discs would
        // reintroduce a horizontal boundary over that continuous background.
        state.shouldRenderDarkDisc = false;
        ringworld$sunVisual = RingSkyCycle.sunVisual(world.getOverworldClockTime() + tickProgress);
        ringworld$cameraY = (float) camera.position().y;
        ringworld$cameraZ = (float) camera.position().z;
        ringworld$cameraX = camera.position().x;

        // Project the one physical star at the ring centre into the camera's
        // tangent frame. Crossing the finite width tilts it toward that point.
        Vec3 starDirection = RingDistortionTuning.centerDirection(geometry, camera.position());
        ringworld$starTiltRadians = (float) Math.atan2(starDirection.z, starDirection.y);
    }

    @Inject(method = {"renderSkyDisc", "renderDarkDisc", "renderSunriseAndSunset"}, at = @At("HEAD"), cancellable = true)
    private void ringworld$hideFlatSky(CallbackInfo ci) {
        if (ClientRingState.geometry() != null) ci.cancel();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void ringworld$hideMoon(MoonPhase phase, float alpha, PoseStack matrices, CallbackInfo ci) {
        if (ClientRingState.geometry() != null) ci.cancel();
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void ringworld$hideCameraRelativeSun(float alpha, PoseStack matrices, CallbackInfo ci) {
        if (ClientRingState.geometry() != null && !ringworld$renderingCenteredSun && notVanilla()) {
            ci.cancel();
        }
    }

    @ModifyConstant(method = "renderSun", constant = @Constant(floatValue = 30.0F), require = 2)
    private float ringworld$shrinkCenteredSun(float vanillaHalfWidth) {
        return ClientRingState.geometry() != null && ringworld$renderingCenteredSun && notVanilla() ? ClientRingState.skyProfile().lightSource().halfWidth() : vanillaHalfWidth;
    }

    @ModifyArg(method = "renderSun", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), index = 1, require = 0)
    private Vector4f ringworld$tintCenteredSun(Vector4f vanillaColor) {
        if (ClientRingState.geometry() == null || !ringworld$renderingCenteredSun || !notVanilla()) {
            return vanillaColor;
        }
        return new Vector4f(ringworld$sunVisual.red(), ringworld$sunVisual.green(), ringworld$sunVisual.blue(), vanillaColor.w() * ringworld$sunVisual.brightness() * (ClientRingState.skyProfile().lightSource() == RingSkyProfile.LightSource.LARGE ? 0.72F : 1.0F));
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
    private void ringworld$renderRingAndSun(PoseStack matrices, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float alpha, float starBrightness, CallbackInfo ci) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;

        RingSurfaceTextureRenderer.render(matrices, geometry, new Vec3(ringworld$cameraX, ringworld$cameraY, ringworld$cameraZ), alpha);

        // Vanilla drew stars after its first sun. The ring covers those stars
        // but stays behind the central star, so redraw the fixed sun once.
        if (ClientRingState.skyProfile().lightSource() != RingSkyProfile.LightSource.NONE && notVanilla()) {
            matrices.pushPose();
            matrices.mulPose(Axis.XP.rotation(ringworld$starTiltRadians));
            matrices.mulPose(Axis.YP.rotationDegrees(-90.0F));
            ringworld$renderingCenteredSun = true;
            try {
                ringworld$invokeRenderSun(alpha, matrices);
            } finally {
                ringworld$renderingCenteredSun = false;
            }
            matrices.popPose();
        }
    }

    @ModifyConstant(method = "renderSunMoonAndStars", constant = @Constant(floatValue = -90))
    private float sunAngle(float constant) {
        if (!notVanilla()) {
            constant += Constants.SUN_ANGLE_OFFSET_Y;
        }
        return constant;
    }

    @Inject(method = "renderSunMoonAndStars", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 2))
    private void resetAngle(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-90));
    }

    @Unique
    private boolean notVanilla() {
        return ClientRingState.skyProfile().lightSource() != RingSkyProfile.LightSource.VANILLA;
    }
}
