// VexCore shader protocol 6. Constants are filled from protocol.properties at build time.
// 26.2 BakedSheetGlyph emits TL, BL, BR, TR. Every quad starts on a multiple of four.
#define VEX_BASE @base@
#define VEX_STRIDE @stride@
#define VEX_Y_COUNT @yCount@
#define VEX_MIN_Y @minY@

vec4 vexAnimatedColor = vec4(-1.0);
float vexOwned = 0.0;

vec4 vex_screen_ui_position(vec4 position, vec2 uv, int vertexId) {
    int encoded = int(floor(position.y / float(VEX_STRIDE))) - VEX_BASE;
    if (encoded < 0 || encoded >= 225 * VEX_Y_COUNT) return position;

    int group = encoded / VEX_Y_COUNT;
    int kind = group / 9;
    float scale = 1.0;
    if (kind >= 9) {
        scale = float(6 + (kind - 9) / 4) / 10.0;
        kind = 5 + (kind - 9) % 4;
    } else if (kind >= 5) scale = 0.5;
    bool panel = kind == 1;
    bool card = kind == 2 || kind == 4 || kind == 6 || kind == 8;
    int anchor = group % 9;
    int localY = encoded % VEX_Y_COUNT + VEX_MIN_Y;
    ivec2 cell = (panel || card) ? ivec2(256, 256) : ivec2(16, 14);
    int corner = vertexId % 4;
    bool bottom = corner == 1 || corner == 2;
    bool right = corner == 2 || corner == 3;

    // Verify two marker pixels in our glyph. A distant vanilla glyph is insufficient.
    ivec2 atlasSize = textureSize(Sampler0, 0);
    ivec2 origin = ivec2(floor(uv * vec2(atlasSize)))
        - ivec2(right ? cell.x - 1 : 0, bottom ? cell.y - 1 : 0);
    if (any(lessThan(origin, ivec2(0))) || any(greaterThanEqual(origin + ivec2(0, 1), atlasSize))) return position;
    ivec4 markerA = ivec4(round(texelFetch(Sampler0, origin, 0) * 255.0));
    ivec4 markerB = ivec4(round(texelFetch(Sampler0, origin + ivec2(0, 1), 0) * 255.0));
    if (any(notEqual(markerA, ivec4(86, 88, 49, 1))) || any(notEqual(markerB, ivec4(67, 79, 82, 1)))) return position;
    vexOwned = 1.0;

    // Tolerance avoids an extra GUI pixel from inverse-projection float round-off.
    vec2 guiSize = ceil(vec2(2.0 / abs(ProjMat[0][0]), 2.0 / abs(ProjMat[1][1])) - 0.001);
    vec2 reference = floor(guiSize * vec2(float(anchor % 3), float(anchor / 3)) * 0.5);
    // Every row has zero total advance; vanilla centers the carrier at GUI width / 2.
    // Visible local X including quad width stays inside (-2048, 2048).
    int transportedY = int(floor((position.x - floor(guiSize.x * 0.5) + 2048.0) / 4096.0));
    position.x -= float(transportedY) * 4096.0;
    localY += transportedY;
    position.x += reference.x - floor(guiSize.x * 0.5);
    // Discard the carrier's Y entirely, including other bossbars' vertical offsets.
    // The two transparent metadata rows precede the visible glyph's local origin.
    position.y = reference.y + float(localY)
        + (bottom ? (panel ? 512.0 : float(cell.y)) : 0.0) - (panel ? 208.0 : card ? 8.0 : 2.0);
    if (kind == 3 || kind == 4 || kind == 7 || kind == 8) {
        ivec3 bytes = ivec3(round(Color.rgb * 255.0));
        int payload = (bytes.r << 16) | (bytes.g << 8) | bytes.b;
        int started = payload & 32767;
        int rgb = payload >> 15;
        // GameTime is game time + partial tick, independent of daylight cycle/player time.
        float age = mod(GameTime * 24000.0 - float(started) + 24000.0, 24000.0);
        // A future timestamp (short transport lead) remains invisible until its start.
        float opacity = smoothstep(0.0, 5.0, age) * (1.0 - smoothstep(65.0, 73.0, age));
        vexAnimatedColor = vec4(vec3((rgb >> 6) & 7, (rgb >> 3) & 7, rgb & 7) / 7.0, opacity);
        position.x += 20.0 * (1.0 - smoothstep(0.0, 5.0, age));
    }
    // Explicit scale is independent of window width; all local geometry shares its anchor pivot.
    position.xy = reference + (position.xy - reference) * scale;
    return position;
}
