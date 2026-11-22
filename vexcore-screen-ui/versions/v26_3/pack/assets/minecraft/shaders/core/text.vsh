#version 330
#extension GL_ARB_separate_shader_objects : require

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#include <minecraft:sample_lightmap.glsl>
#endif

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 3) in ivec2 UV2;
#endif

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
uniform sampler2D Sampler2;
layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#endif

layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;

#if defined(IS_GUI) && !defined(IS_GRAYSCALE)
uniform sampler2D Sampler0;
#include <minecraft:globals.glsl>
layout(location = 4) flat out float vexUi;
layout(location = 5) out vec2 vexPanelPoint;
layout(location = 6) flat out vec2 vexPanelSize;
layout(location = 7) flat out float vexPanelRadius;
layout(location = 8) flat out float vexSolidPixel;
layout(location = 9) out vec2 vexRewardPoint;
layout(location = 10) flat out float vexRewardFrame;
layout(location = 11) flat out float vexRewardAge;
layout(location = 12) out vec2 vexProgressionCell;
layout(location = 13) flat out int vexProgressionPart;
#include <vexcore:screen_ui/v26_3.glsl>
#include <vexcore:reward_cards/v26_3.glsl>
#include <vexcore:progression/v26_3.glsl>
#endif

void main() {
    vec4 position = ModelViewMat * vec4(Position, 1.0);
#if defined(IS_GUI) && !defined(IS_GRAYSCALE)
    vexPanelPoint = vec2(0.0);
    vexPanelSize = vec2(0.0);
    vexPanelRadius = 0.0;
    vexSolidPixel = 0.0;
    vexRewardPoint = vec2(0.0);
    vexRewardFrame = 0.0;
    vexRewardAge = -1.0;
    vexProgressionCell = vec2(0.0);
    vexProgressionPart = -1;
    position = vex_reward_card_position(position, UV0, gl_VertexIndex);
    if (vexOwned < 0.5) {
        position = vex_progression_position(position, UV0, gl_VertexIndex);
    }
    if (vexOwned < 0.5) {
        position = vex_dialog_art_position(position, UV0);
    }
    if (vexOwned < 0.5) {
        position = vex_screen_ui_position(position, UV0, gl_VertexIndex);
    }
#endif
    gl_Position = ProjMat * position;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
#else
    vertexColor = Color;
#endif
#if defined(IS_GUI) && !defined(IS_GRAYSCALE)
    vexUi = vexOwned;
    if (vexAnimatedColor.a >= 0.0) vertexColor = vexAnimatedColor;
#endif
    texCoord0 = UV0;
}

