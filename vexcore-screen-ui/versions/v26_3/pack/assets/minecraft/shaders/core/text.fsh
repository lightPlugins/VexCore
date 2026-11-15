#version 330
#extension GL_ARB_separate_shader_objects : require

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#endif

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif

layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif
#if defined(IS_GUI) && !defined(IS_GRAYSCALE)
layout(location = 4) flat in float vexUi;
layout(location = 5) in vec2 vexPanelPoint;
layout(location = 6) flat in vec2 vexPanelSize;
layout(location = 7) flat in float vexPanelRadius;
layout(location = 8) flat in float vexSolidPixel;
layout(location = 9) in vec2 vexRewardPoint;
layout(location = 10) flat in float vexRewardFrame;
layout(location = 11) flat in float vexRewardAge;
layout(location = 12) in vec2 vexProgressionCell;
layout(location = 13) flat in int vexProgressionPart;

int vex_progression_piece(vec2 point) {
    return (point.x > 2.8 + (point.y - 8.0) * 0.26 ? 1 : 0)
        + (point.y > 8.5 - point.x * 0.37 ? 2 : 0);
}

float vex_progression_ink(vec2 offset, int piece) {
    vec2 point = vexProgressionCell - offset;
    if (any(lessThan(point, vec2(0.0))) || any(greaterThanEqual(point, vec2(18.0, 22.0)))) return 0.0;
    if (piece >= 0 && vex_progression_piece(point) != piece) return 0.0;
    float alpha = texture(Sampler0, texCoord0 - offset / vec2(textureSize(Sampler0, 0))).a;
    return alpha * step(0.01, alpha);
}

float vex_progression_shine(float diagonal, float start, float duration, float direction) {
    float progress = (vexRewardAge - start) / duration;
    float distance = abs(diagonal * direction - mix(-134.0, 134.0, clamp(progress, 0.0, 1.0)));
    float tail = 1.0 - smoothstep(0.0, 24.0, distance);
    float core = 1.0 - smoothstep(1.0, 6.0, distance);
    return (0.52 * tail + 0.48 * core) * step(0.0, progress) * (1.0 - step(1.0, progress));
}

vec4 vex_experience_bar() {
    vec2 point = vexProgressionCell;
    vec2 distance = abs(point - vec2(68.0, 4.0)) - vec2(65.0, 1.0);
    float edge = length(max(distance, vec2(0.0))) + min(max(distance.x, distance.y), 0.0);
    float alpha = 1.0 - smoothstep(2.4, 3.0, edge);
    float rim = smoothstep(1.3, 2.4, edge);
    float fill = mix(vexRewardPoint.x, vexRewardPoint.y, smoothstep(6.0, 25.0, vexRewardAge));
    float head = 2.0 + 132.0 * fill;
    float ink = (1.0 - smoothstep(head - 0.5, head + 0.5, point.x))
        * (1.0 - rim) * step(0.001, fill);
    float gleam = (1.0 - smoothstep(0.0, 12.0, abs(point.x - head)))
        * (1.0 - smoothstep(25.0, 34.0, vexRewardAge));
    vec3 track = mix(vec3(0.10, 0.065, 0.17), vec3(0.43, 0.30, 0.62), rim);
    vec3 gold = mix(vec3(0.68, 0.37, 0.68), vec3(1.0, 0.84, 0.49), 1.0 - point.y / 8.0);
    gold = mix(gold, vec3(1.0, 0.95, 0.72), gleam);
    return vec4(mix(track, gold, ink), vertexColor.a * alpha) * ColorModulator;
}
#endif

void main() {
#ifdef IS_GRAYSCALE
    vec4 texColor = texture(Sampler0, texCoord0).rrrr;
#else
    vec4 texColor = texture(Sampler0, texCoord0);
#endif

    vec4 color = texColor * vertexColor * ColorModulator;
#if defined(IS_GUI) && !defined(IS_GRAYSCALE)
    if (vexPanelSize.x > 0.0) {
        vec2 distance = abs(vexPanelPoint - vexPanelSize * 0.5)
            - (vexPanelSize * 0.5 - vec2(vexPanelRadius));
        if (length(max(distance, vec2(0.0))) + min(max(distance.x, distance.y), 0.0) > vexPanelRadius) discard;
        color = vertexColor * ColorModulator;
    }
    if (vexSolidPixel > 0.5) color = vertexColor * ColorModulator;
    if (vexProgressionPart == 8) {
        color = vex_experience_bar();
    } else if (vexProgressionPart >= 0) {
        bool shard = vexProgressionPart >= 2 && vexProgressionPart <= 5;
        int piece = shard ? vexProgressionPart - 2 : -1;
        float ink = texColor.a * step(0.01, texColor.a);
        if (shard && vex_progression_piece(vexProgressionCell) != piece) ink = 0.0;
        vec3 face = color.rgb;
        float backing = 0.0;
        if (vexProgressionPart != 6) {
            // Sample inside the marked cell to keep the shadow on the same animation and glyph quad.
            float offset = vexProgressionPart == 0 ? 1.0 : 0.65;
            float shadow = vex_progression_ink(vec2(offset, offset * 2.0), piece);
            float penumbra = max(vex_progression_ink(vec2(0.0, offset * 2.0), piece),
                vex_progression_ink(vec2(offset * 2.0, offset * 2.0), piece));
            backing = max(shadow * 0.90, penumbra * 0.52) * (1.0 - ink);
        }
        if (vexProgressionPart <= 1) {
            float diagonal = vexRewardPoint.x + vexRewardPoint.y * 0.68;
            float gold = vex_progression_shine(diagonal, 13.0, 16.0, 1.0);
            float violet = vex_progression_shine(diagonal, 36.0, 18.0, -1.0);
            float white = vex_progression_shine(diagonal, 62.0, 19.0, 1.0);
            float flash = (1.0 - smoothstep(11.0, 14.5, vexRewardAge)) * step(10.0, vexRewardAge);
            face = mix(face, vec3(1.0, 0.96, 0.73), max(gold, flash * 0.68));
            face = mix(face, vec3(0.90, 0.76, 1.0), violet);
            face = mix(face, vec3(1.0, 0.99, 0.95), white);
            if (vexRewardAge >= 82.0) {
                // Break the face and shadow with the same moving threshold so they leave together.
                vec2 pixel = floor(vexRewardPoint * 0.72);
                float grain = fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
                float dissolve = (vexRewardAge - 86.0 - abs(vexRewardPoint.x) * 0.035
                    - max(vexRewardPoint.y, 0.0) * 0.052) / 10.0;
                float threshold = grain * 0.70 + 0.20;
                float mask = 1.0 - smoothstep(threshold, threshold + 0.10, dissolve);
                float ember = smoothstep(threshold - 0.20, threshold, dissolve);
                float flare = smoothstep(82.0, 87.0, vexRewardAge)
                    * (1.0 - smoothstep(87.0, 92.0, vexRewardAge));
                face = mix(face, vec3(1.0, 0.97, 0.78), flare * 0.82);
                face = mix(face, vec3(0.80, 0.54, 1.0), ember);
                ink *= mask;
                backing *= mask;
            }
        } else if (shard) {
            face = mix(face, vec3(1.0, 0.70, 0.32),
                smoothstep(10.0, 11.5, vexRewardAge) * (1.0 - smoothstep(14.0, 20.0, vexRewardAge)));
        }
        float coverage = ink + backing;
        color = vec4((face * ink + vec3(0.075, 0.035, 0.115) * backing) / max(coverage, 0.001),
            coverage * vertexColor.a * ColorModulator.a);
    } else if (vexRewardFrame > 0.5) {
        float edge = min(min(vexRewardPoint.x, 224.0 - vexRewardPoint.x),
            min(vexRewardPoint.y, 40.0 - vexRewardPoint.y));
        float sweep = mix(-32.0, 280.0, clamp((vexRewardAge - 3.0) / 11.0, 0.0, 1.0));
        float band = 1.0 - smoothstep(0.0, 28.0, abs(vexRewardPoint.x + vexRewardPoint.y * 0.65 - sweep));
        float shine = band * (1.0 - step(4.0, edge)) * step(3.0, vexRewardAge)
            * (1.0 - step(14.0, vexRewardAge));
        color.rgb = min(vec3(1.0), color.rgb + vec3(0.50, 0.40, 0.64) * shine);
    }
#endif
    float cutoff = 0.1;
#if defined(IS_GUI) && !defined(IS_GRAYSCALE)
    if (vexUi > 0.5) cutoff = 0.005;
#endif
    if (color.a < cutoff) {
        discard;
    }

#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
#ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
#endif
#if !defined(IS_SEE_THROUGH) && !defined(IS_GUI)
#ifdef OIT_ACCUMULATE
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
#else
    vec4 fogColor = FogColor;
#endif
    color = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
#endif
    fragColor = color;
#endif
}
