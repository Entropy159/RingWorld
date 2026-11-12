#version 330
#extension GL_ARB_separate_shader_objects : require

// ColorLight copy of sodium:blocks/block_layer_opaque.vsh.
// Everything marked COLORLIGHT is ours; the rest must stay identical to Sodium's shader.
// Only the shared includes (globals, fog, chunk_vertex) are pulled from Sodium, so changes to the
// vertex format are picked up automatically.

#include <sodium:globals.glsl>
#include <sodium:fog.glsl>
#include <sodium:chunk_vertex.glsl>

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

// Mirrors RingNearbyProjection: unit arc spacing nearby, positive spacing everywhere.
float ring_trial_angle(float delta, float vertexY) {
    float circumference = float(RingWorldLayout.y);
    float u = mod(delta + circumference * 0.5, circumference) - circumference * 0.5;
    float a = abs(u), core = RingWorldDistortion.y, end = RingWorldDistortion.z;
    float nearSlope = 1.0 / max(RingWorldDistortion.w + RingWorldVertical.x - vertexY,
            core * 2.0);
    float midpoint = (core + end) * 0.5;
    float farSlope = (3.141592653589793 - nearSlope * midpoint) / (circumference * 0.5 - midpoint);
    float length = end - core;
    float value;
    if (a <= core) value = nearSlope * a;
    else if (a < end) {
        float t = (a - core) / length;
        value = nearSlope * a + (farSlope - nearSlope) * length * (t*t*t - 0.5*t*t*t*t);
    } else value = nearSlope * midpoint + farSlope * (length * 0.5 + a - end);
    return sign(u) * value;
}
vec3 ring_trial_position(vec3 point, vec3 camera) {
    float angle = ring_trial_angle(point.x - camera.x, point.y);
    float radius = RingWorldDistortion.w + RingWorldVertical.x - point.y;
    float cameraRadius = RingWorldDistortion.w + RingWorldVertical.x - camera.y;
    return vec3(radius * sin(angle), cameraRadius - radius * cos(angle), point.z - camera.z);
}

// Canonical world coordinates to camera-local circumference, up and width.
vec3 ring_project_position(vec3 vertexCanonical, vec3 cameraCanonical) {
    if (RingWorldDistortion.x > 0.5) return ring_trial_position(vertexCanonical, cameraCanonical);
    float circumference = float(RingWorldLayout.y);
    float baseRadius = circumference / (2.0 * 3.14159265358979323846);
    float deltaAngle = 6.28318530717958647692
    * (vertexCanonical.x - cameraCanonical.x) / circumference;
    float vertexRadius = baseRadius + RingWorldVertical.x - vertexCanonical.y;
    float cameraRadius = baseRadius + RingWorldVertical.x - cameraCanonical.y;
    return vec3(
            vertexRadius * sin(deltaAngle),
            cameraRadius - vertexRadius * cos(deltaAngle),
            vertexCanonical.z - cameraCanonical.z
    );
}

layout(location = 7) out vec2 ringworldDistances;
#include <minecraft:globals.glsl>

layout(location = 0) out vec4 v_Color;
layout(location = 1) out vec2 v_TexCoord;

#ifdef USE_FOG
layout(location = 2) out vec2 v_FragDistance;
layout(location = 3) out float v_fadeFactor;
#endif

// COLORLIGHT: what the fragment shader needs to light the pixel itself
#ifndef OIT_ALPHA_ONLY
#include <colorlight:colorlight_data.glsl>
layout(location = 4) out vec4 v_ColorBase;
layout(location = 5) out vec2 v_LightUV;
layout(location = 6) out vec3 v_CLRel;
#endif

uniform isamplerBuffer u_SectionTimeInfo;

#ifdef VULKAN
layout(push_constant) uniform PC {
    vec3 u_RegionOffset;
    int u_CurrentTime;
    uint u_RegionID;
};
#else
uniform vec3 u_RegionOffset;
uniform int u_CurrentTime;
uniform uint u_RegionID;
#endif

#ifndef OIT_ALPHA_ONLY
uniform sampler2D u_LightTex; // The light map texture sampler
#endif

uvec3 _get_relative_chunk_coord(uint pos) {
    // Packing scheme is defined by LocalSectionIndex
    return uvec3(pos) >> uvec3(5u, 0u, 2u) & uvec3(7u, 3u, 7u);
}

vec3 _get_draw_translation(uint pos) {
    return _get_relative_chunk_coord(pos) * vec3(16.0);
}

void main() {
    _vert_init();

    // Transform the chunk-local vertex position into world model space
    vec3 translation = u_RegionOffset + _get_draw_translation(_draw_id);
    vec3 position = (_vert_position + translation) - u_CameraOffset;
    vec3 ringworldFlat = position;
    ringworldDistances = vec2(-1.0);
    if (RingWorldLayout.x != 0) {
        vec3 ringworldCamera = vec3(0.0, float(CameraBlockPos.y) - CameraOffset.y, 0.0);
        ringworldDistances = vec2(length(position.xz), length(position));
        position = ring_project_position(position + ringworldCamera, ringworldCamera);
    }


    #ifdef USE_FOG
    v_FragDistance = getFragDistance(position);
    if (RingWorldLayout.x != 0) {
        v_FragDistance = 1.02 * max(v_FragDistance, getFragDistance(ringworldFlat));
    }


    int chunkId = int(_draw_id);
    int chunkFade = texelFetch(u_SectionTimeInfo, int((u_RegionID * 256u) + uint(chunkId))).r;
    int fadeTime = u_CurrentTime - chunkFade;
    float elapsed = float(fadeTime);
    float fade = clamp(float(u_CurrentTime - chunkFade) * u_FadePeriodInv, 0.0, 1.0);
    v_fadeFactor = (chunkFade < 0) ? 1.0 : fade;
    #endif

    // Transform the vertex position into model-view-projection space
    gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);

    // Add the light color to the vertex color, and pass the texture coordinates to the fragment shader
    #ifndef OIT_ALPHA_ONLY
    v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);


    // COLORLIGHT: vanilla result above stays as the fallback; these let the fragment shader redo it
    v_ColorBase = _vert_color;
    v_LightUV = _vert_tex_light_coord;
    v_CLRel = position + cl_cameraFrac(); // position relative to the camera's integer block
    #else
    v_Color = _vert_color;
    #endif

    v_TexCoord = (_vert_tex_diffuse_coord_bias * u_TexCoordShrink) + _vert_tex_diffuse_coord; // FMA for precision
}
