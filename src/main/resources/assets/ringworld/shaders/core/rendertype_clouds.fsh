#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <ringworld:render_uniforms.glsl>

in float vertexDistance;
in vec4 vertexColor;
in float ringIntrinsicWorldZ;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor;
    if (RingWorldLayout.x != 0
            && (ringIntrinsicWorldZ < RingWorldAtmosphere2.z
                || ringIntrinsicWorldZ > RingWorldAtmosphere2.w)) {
        discard;
    }
    color.a *= 1.0f - linear_fog_value(vertexDistance, 0, FogCloudsEnd);
    if (RingWorldLayout.x != 0) {
        float fadeDistance = RingWorldCloudFade;
        float edgeDistance = fadeDistance;
        if (ringIntrinsicWorldZ < 0) {
            edgeDistance = -RingWorldAtmosphere2.z + ringIntrinsicWorldZ;
        } else {
            edgeDistance = RingWorldAtmosphere2.w - ringIntrinsicWorldZ;
        }
        if (edgeDistance <= fadeDistance) {
            float fade = edgeDistance / fadeDistance;
            color.a *= linear_fog_value(fade, 0, 1);
        }
    }
    fragColor = color;
}
