#version 460 core

// ColorLight copy of sodium:blocks/block_layer_opaque.fsh.
// Everything marked COLORLIGHT is ours; the rest must stay identical to Sodium's shader.

#include <sodium:globals.glsl>
#include <sodium:fog.glsl>
#include <sodium:chunk_material.glsl>
#include <minecraft:oit.glsl>

// RingWorld terrain adapter
#ifndef RINGWORLD_RENDER_UNIFORMS_GLSL
#define RINGWORLD_RENDER_UNIFORMS_GLSL
layout(std140) uniform RingWorld {
    ivec4 RingWorldLayout;
    vec4 RingWorldVertical;
    vec4 RingWorldRender;
    vec4 RingWorldHandoff;
    vec4 RingWorldDetail;
    vec4 RingWorldAtmosphere;
    vec4 RingWorldAtmosphere2;
// mode (0 midpoint, 1 gamma), falloff exponent, peak strength, reserved
    vec4 RingWorldAtlasLight;
    vec4 RingWorldWaterStill;
    vec4 RingWorldWaterFlow;
// enabled, correction distance, blend end, safe visual reference radius
    vec4 RingWorldDistortion;
    float RingWorldCloudFade;
};
#endif

// Requires the shared globals and fog uniforms. Both sides of the handoff
// use the same atmospheric tint and reveal curve; no extra texture fetches.
float ring_handoff_smootherstep(float start, float end, float value) {
    float t = clamp((value - start) / max(0.0001, end - start), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

vec3 ring_handoff_edge_color(vec3 fogColor) {
    // Backdrop id shares the previously unused fourth Atlas-light component.
    if (RingWorldAtlasLight.w > 1.5) return vec3(1.0, 1.0, 3.0) / 255.0;
    if (RingWorldAtlasLight.w > 0.5) return vec3(5.0, 8.0, 16.0) / 255.0;
    return fogColor;
}

float ring_handoff_reveal(float distance) {
    float detail = ring_handoff_smootherstep(RingWorldDetail.x, RingWorldDetail.y, distance);
    float reveal = mix(RingWorldDetail.z, RingWorldDetail.w, detail);
    float fraction = clamp(distance / (float(RingWorldLayout.y) * 0.5), 0.0, 1.0);
    float haze = mix(RingWorldAtmosphere.x, RingWorldAtmosphere.y,
            pow(fraction, RingWorldAtmosphere.z));
    // Introduce far haze continuously instead of a pale near-Atlas belt.
    return reveal * (1.0 - haze * detail);
}

// Requires render_uniforms.glsl and handoff.glsl. Shared by material adapters.
float ring_material_smootherstep(float edge0, float edge1, float value) {
    float t = clamp((value - edge0) / max(0.0001, edge1 - edge0), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

// Ordered coverage gives every 8x8 pixel area an even fade, without the
// irregular speckle of a screen-space hash. Keep the pattern fixed in screen
// space so motion does not introduce a new random threshold each frame.
float ring_material_dither_threshold(vec2 pixel) {
    ivec2 cell = ivec2(pixel) & ivec2(7);
    int rank = 0;
    for (int bit = 0; bit < 3; ++bit) {
        int x = (cell.x >> bit) & 1;
        int y = (cell.y >> bit) & 1;
        int pair = ((x ^ y) << 1) | y;
        rank |= pair << (4 - 2 * bit);
    }
    return (float(rank) + 0.5) / 64.0;
}

bool ring_material_water_sprite(vec2 uv, vec4 bounds) {
    return bounds.x < bounds.z && bounds.y < bounds.w
    && all(greaterThanEqual(uv, bounds.xy)) && all(lessThanEqual(uv, bounds.zw));
}


vec4 ring_material_coverage(vec4 color, vec4 vertexColor, vec2 texCoord0,
        float ringIntrinsicDistance, float ringHandoffDistance) {
    if ((RingWorldLayout.x != 0) && ringIntrinsicDistance >= 0.0) {
        if (ring_material_water_sprite(texCoord0, RingWorldWaterStill)
            || ring_material_water_sprite(texCoord0, RingWorldWaterFlow)) {
            float waterMatch = ring_material_smootherstep(RingWorldHandoff.z, RingWorldHandoff.x,
                    ringIntrinsicDistance);
            color.a = mix(color.a, 1.0, waterMatch);
            vec3 atlasWater = vertexColor.rgb * 0.58;
            float waterPeak = max(atlasWater.r, max(atlasWater.g, atlasWater.b));
            atlasWater = mix(atlasWater, vec3(waterPeak), 0.12) * 1.15;
            color.rgb = mix(color.rgb, atlasWater, waterMatch);
        }
        float proxyReveal = ring_material_smootherstep(
                RingWorldHandoff.x,
                RingWorldHandoff.y,
                ringHandoffDistance
        );
        if (ring_material_dither_threshold(gl_FragCoord.xy) < proxyReveal) {
            discard;
        }
    }
    return color;
}

vec4 ring_material_atmosphere(vec4 color, float distance, vec3 fogColor) {
    if (RingWorldLayout.x != 0 && distance >= 0.0) {
        color.rgb = mix(ring_handoff_edge_color(fogColor), color.rgb, ring_handoff_reveal(distance));
    }
    return color;
}

layout(location = 7) in vec2 ringworldDistances;

layout(location = 0) in vec4 v_Color; // The interpolated vertex color
layout(location = 1) in vec2 v_TexCoord; // The interpolated block texture coordinates
#ifdef USE_FOG
layout(location = 2) in vec2 v_FragDistance; // The fragment's distance from the camera (cylindrical and spherical)
layout(location = 3) in float v_fadeFactor;
#endif

// COLORLIGHT: per-pixel coloured block light
#ifndef OIT_ALPHA_ONLY
layout(location = 4) in vec4 v_ColorBase; // vertex colour without the lightmap
layout(location = 5) in vec2 v_LightUV;   // Sodium's lightmap coordinate (block, sky)
layout(location = 6) in vec3 v_CLRel;     // position relative to the camera's integer block

uniform sampler2D u_LightTex;

#define CL_FRAGMENT
#include <colorlight:colorlight_data.glsl>
#endif

uniform sampler2D u_BlockTex; // The block texture

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor; // The output fragment for the color framebuffer
#endif

vec4 calculateFinalColor(vec4 color) {
    if (RingWorldLayout.x != 0 && ringworldDistances.x >= 0.0) {
        color = ring_material_atmosphere(color, ringworldDistances.x, u_FogColor.rgb);
        color = _linearFog(color, v_FragDistance, u_FogColor,
                u_EnvironmentFog, vec2(1.0e20, 1.0e21), 1.0);
        #ifdef OIT_ACCUMULATE
        color = sampleColorForAccumulation(color);
        #endif
        return color;
    }

    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(u_FogColor.rgb * color.a, u_FogColor.a);
    #else
    vec4 fogColor = u_FogColor;
    #endif

    #ifdef OIT_ALPHA_ONLY
    float factor = 1.0;
    #else
    float factor = v_fadeFactor;
    #endif

    return _linearFog(color, v_FragDistance, fogColor, u_EnvironmentFog, u_RenderFog, factor);
}
vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize, vec2 du, vec2 dv, vec2 texelScreenSize) {
    // Convert our UV back up to texel coordinates and find out how far over we are from the center of each pixel
    vec2 uvTexelCoords = uv / pixelSize;
    vec2 texelCenter = round(uvTexelCoords) - 0.5f;
    vec2 texelOffset = uvTexelCoords - texelCenter;

    // Move our offset closer to the texel center based on texel size on screen
    texelOffset = (texelOffset - 0.5f) * pixelSize / texelScreenSize + 0.5f;
    texelOffset = clamp(texelOffset, 0.0f, 1.0f);

    uv = (texelCenter + texelOffset) * pixelSize;
    return textureGrad(source, uv, du, dv);
}

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    return sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
}

// Rotated Grid Super-Sampling
vec4 sampleRGSS(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);

    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    float maxTexelSize = max(texelScreenSize.x, texelScreenSize.y);

    float minPixelSize = min(pixelSize.x, pixelSize.y);

    float transitionStart = minPixelSize * 1.0;
    float transitionEnd = minPixelSize * 2.0;
    float blendFactor = smoothstep(transitionStart, transitionEnd, maxTexelSize);

    float duLength = length(du);
    float dvLength = length(dv);
    float minDerivative = min(duLength, dvLength);
    float maxDerivative = max(duLength, dvLength);

    float effectiveDerivative = sqrt(minDerivative * maxDerivative);

    float mipLevelExact = max(0.0, log2(effectiveDerivative / minPixelSize));

    const vec2 offsets[4] = vec2[](
            vec2(0.125, 0.375),
            vec2(-0.125, -0.375),
            vec2(0.375, -0.125),
            vec2(-0.375, 0.125)
    );

    vec4 rgssColor = vec4(0.0);
    for (int i = 0; i < 4; ++i) {
        vec2 sampleUV = uv + offsets[i] * pixelSize;
        rgssColor += textureLod(source, sampleUV, mipLevelExact);
    }
    rgssColor *= 0.25;

    vec4 nearestColor = sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);

    return mix(nearestColor, rgssColor, blendFactor);
}

#ifndef OIT_ALPHA_ONLY
// COLORLIGHT: the lightmap look (cl_lightColor) lives in colorlight_light.glsl, shared with Nvidium's shaders
#define CL_LIGHT_TEX u_LightTex
#include <colorlight:colorlight_light.glsl>

// Replaces Sodium's per-vertex "vertex colour * lightmap" (v_Color) for every pixel while the pipeline is enabled.
vec4 cl_resolveVertexColor() {
    if (!cl_enabled()) {
        return v_Color;
    }

    // Smooth or flat light, decided by the header. The normal comes from screen-space derivatives (taken in uniform
    // control flow: the enabled flag is in the header), flipped to face the camera.
    vec3 n = cross(dFdx(v_CLRel), dFdy(v_CLRel));
    float nLength = length(n);
    n = (nLength > 1.0e-12) ? (n / nLength) : vec3(0.0, 1.0, 0.0);
    if (dot(n, v_CLRel - cl_cameraFrac()) > 0.0) {
        n = -n;
    }
    vec3 cl = cl_fetchLight(v_CLRel, n, cl_cameraBlock());
    float lvl = max(cl.r, max(cl.g, cl.b));

    return v_ColorBase * vec4(cl_lightColor(v_LightUV, cl, lvl), 1.0);
}
#endif

void main() {
    vec4 color = u_UseRGSS ? sampleRGSS(u_BlockTex, v_TexCoord, u_TexelSize) : sampleNearest(u_BlockTex, v_TexCoord, u_TexelSize);
    #ifdef OIT_ALPHA_ONLY
    color *= v_Color; // Apply per-vertex color modulator
    #else
    color *= cl_resolveVertexColor(); // COLORLIGHT: per-pixel light instead of the per-vertex one
    #endif

    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif
    color = ring_material_coverage(color, v_Color, v_TexCoord,
            ringworldDistances.x, ringworldDistances.y);

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
