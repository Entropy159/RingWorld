package dev.ringworld.client.mixin;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import dev.ringworld.client.render.RingShaderContract;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Adds only our private binding to materials which consume it.
 */
@Mixin(RenderPipeline.Builder.class)
public abstract class RingPipelineUniformsMixin {
    @Shadow
    public abstract RenderPipeline.Builder withBindGroupLayout(BindGroupLayout bindGroupLayout);

    @Shadow
    @Final
    private Map<ShaderType, Identifier> shaders;
    @Unique
    private boolean ringworld$bindingAdded;

    @Inject(method = "build", at = @At("HEAD"))
    private void ringworld$declarePrivateUniforms(CallbackInfoReturnable<RenderPipeline> cir) {
        var builder = (RenderPipeline.Builder) (Object) this;
        var vertex = shaders.get(ShaderType.VERTEX);
        var fragment = shaders.get(ShaderType.FRAGMENT);
        if (vertex != null) builder.withVertexShader(RingShaderContract.materialShader(vertex));
        if (fragment != null) builder.withFragmentShader(RingShaderContract.materialShader(fragment));
        if (!ringworld$bindingAdded && RingShaderContract.usesRingUniforms(shaders.get(ShaderType.VERTEX))) {
            withBindGroupLayout(BindGroupLayout.builder().withUniform(RingShaderContract.UNIFORM, UniformType.UNIFORM_BUFFER).build());
            ringworld$bindingAdded = true;
        }
    }
}
