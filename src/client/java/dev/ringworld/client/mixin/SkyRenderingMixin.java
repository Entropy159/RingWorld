package dev.ringworld.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
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
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Fixed toned sun plus the active texture-backed complete-ring surface.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRenderingMixin {
    @Final
    @Shadow
    private RenderTarget renderTarget;

    @Invoker("renderSun")
    protected abstract void ringworld$invokeRenderSun(RenderPass pass, float alpha, PoseStack matrices);

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
            state.sunriseAndSunsetColor = new Vector4f();
        }
        state.starAngle = RingSkyCycle.starFieldAngleRadians(geometry, camera.position().x);
        RingSkyProfile skyProfile = ClientRingState.skyProfile();
        switch (skyProfile.backdrop()) {
            case ATMOSPHERE -> {
            }
            case NIGHT -> {
                state.skyColor = new Vector3f(5 / 255f, 8 / 255f, 16 / 255f);
                state.starBrightness = Math.max(state.starBrightness, 0.88F);
            }
            case VOID -> {
                state.skyColor = new Vector3f(1 / 255f, 1 / 255f, 3 / 255f);
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
    private void ringworld$hideMoon(RenderPass pass, MoonPhase phase, float alpha, PoseStack matrices, CallbackInfo ci) {
        if (ClientRingState.geometry() != null) ci.cancel();
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void ringworld$hideCameraRelativeSun(RenderPass pass, float alpha, PoseStack matrices, CallbackInfo ci) {
        if (ClientRingState.geometry() != null && !ringworld$renderingCenteredSun && notVanilla()) {
            ci.cancel();
        }
    }

    @ModifyConstant(method = "renderSun", constant = @Constant(floatValue = 30.0F), require = 1)
    private float ringworld$shrinkCenteredSun(float vanillaHalfWidth) {
        return ClientRingState.geometry() != null && ringworld$renderingCenteredSun && notVanilla() ? ClientRingState.skyProfile().lightSource().halfWidth() : vanillaHalfWidth;
    }

    @ModifyArg(method = "renderSun", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"), index = 1)
    private Vector4f ringworld$tintCenteredSun(Vector4f vanillaColor) {
        if (ClientRingState.geometry() == null || !ringworld$renderingCenteredSun || !notVanilla()) {
            return vanillaColor;
        }
        return new Vector4f(ringworld$sunVisual.red(), ringworld$sunVisual.green(), ringworld$sunVisual.blue(), vanillaColor.w() * ringworld$sunVisual.brightness() * (ClientRingState.skyProfile().lightSource() == RingSkyProfile.LightSource.LARGE ? 0.72F : 1.0F));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void ringworld$renderRingAndSun(GpuBufferSlice skyFog, SkyRenderState state, CallbackInfo ci) {
        PoseStack matrices = new PoseStack();
        float alpha = state.rainBrightness;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;

        RingSurfaceTextureRenderer.render(matrices, geometry, new Vec3(ringworld$cameraX, ringworld$cameraY, ringworld$cameraZ), alpha);

        // Vanilla drew stars after its first sun. The ring covers those stars
        // but stays behind the central star, so redraw the fixed sun once.
        if (ClientRingState.skyProfile().lightSource() != RingSkyProfile.LightSource.NONE && notVanilla()) {
            matrices.pushPose();
            matrices.rotate(Axis.XP.rotation(ringworld$starTiltRadians));
            matrices.rotate(Axis.YP.rotationDegrees(-90.0F));
            ringworld$renderingCenteredSun = true;
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(() -> "RingWorld centered sun", renderTarget.getColorTextureView(), Optional.empty(), renderTarget.getDepthTextureView(), OptionalDouble.empty())) {
                ringworld$invokeRenderSun(pass, alpha, matrices);
            } finally {
                ringworld$renderingCenteredSun = false;
            }
            encoder.submit();
            matrices.popPose();
        }
    }

    @ModifyConstant(method = "renderSunMoonAndStars", constant = @Constant(floatValue = -90))
    private float sunAngleY(float constant) {
        if (!notVanilla()) {
            constant += ClientRingState.sunAngleOffsetY();
        }
        return constant;
    }

    @Inject(method = "renderSunMoonAndStars", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 1))
    private void sunAngleZ(RenderPass renderPass, PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        if (!notVanilla()) {
            poseStack.pushPose();
            poseStack.rotate(Axis.ZP.rotationDegrees(ClientRingState.sunAngleOffsetZ()));
        }
    }

    @Inject(method = "renderSunMoonAndStars", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 2))
    private void resetAngle(RenderPass renderPass, PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        poseStack.popPose();
        if (!notVanilla()) {
            poseStack.popPose(); //Remove Z rotation
        }
        poseStack.pushPose();
        poseStack.rotate(Axis.YP.rotationDegrees(-90));
    }

    @Unique
    private boolean notVanilla() {
        return ClientRingState.skyProfile().lightSource() != RingSkyProfile.LightSource.VANILLA;
    }
}
