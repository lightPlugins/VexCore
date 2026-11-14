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
in vec2 vexRewardPoint;
flat in float vexRewardFrame;
flat in float vexRewardAge;
in vec2 vexProgressionCell;
flat in int vexProgressionPart;

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
    if (vexProgressionPart >= 0) {
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

#ifdef IS_SEE_THROUGH
    fragColor = color * ColorModulator;
#elif defined(IS_GUI)
    fragColor = color;
#else
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
#endif
}
