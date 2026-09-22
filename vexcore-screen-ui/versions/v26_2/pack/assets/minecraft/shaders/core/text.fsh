#version 330

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#moj_import <minecraft:fog.glsl>
#endif

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#endif

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;
#if defined(IS_GUI) && !defined(IS_GRAYSCALE)
flat in float vexUi;
in vec2 vexPanelPoint;
flat in vec2 vexPanelSize;
flat in float vexPanelRadius;
flat in float vexSolidPixel;
#endif

void main() {
#ifdef IS_GRAYSCALE
    vec4 texColor = texture(Sampler0, texCoord0).rrrr;
#else
    vec4 texColor = texture(Sampler0, texCoord0);
#endif

#ifdef IS_SEE_THROUGH
    vec4 color = texColor * vertexColor;
#else
    vec4 color = texColor * vertexColor * ColorModulator;
#endif
#if defined(IS_GUI) && !defined(IS_GRAYSCALE)
    if (vexPanelSize.x > 0.0) {
        vec2 distance = abs(vexPanelPoint - vexPanelSize * 0.5)
            - (vexPanelSize * 0.5 - vec2(vexPanelRadius));
        if (length(max(distance, vec2(0.0))) + min(max(distance.x, distance.y), 0.0) > vexPanelRadius) discard;
        color = vertexColor * ColorModulator;
    }
    if (vexSolidPixel > 0.5) color = vertexColor * ColorModulator;
#endif
    float cutoff = 0.1;
#if defined(IS_GUI) && !defined(IS_GRAYSCALE)
    if (vexUi > 0.5) cutoff = 0.005;
#endif
    if (color.a < cutoff) {
        discard;
    }

#ifdef IS_SEE_THROUGH
    fragColor = color * ColorModulator;
#elif defined(IS_GUI)
    fragColor = color;
#else
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
#endif
}
