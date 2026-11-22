// VexCore shader protocol 10. Constants are filled from protocol.properties at build time.
#define VEX_BASE @base@
#define VEX_STRIDE @stride@
#define VEX_Y_COUNT @yCount@
#define VEX_MIN_Y @minY@

vec4 vexAnimatedColor = vec4(-1.0);
float vexOwned = 0.0;

// A GUI sprite UV is on the exact boundary, shared with neighboring packed sprites.
// Check the texels on the object's own side even when it touches another panorama.
bool vex_dialog_atlas_marker(ivec2 boundary, ivec2 atlas, bool right, bool bottom) {
    ivec2 point = boundary - ivec2(right ? 1 : 0, bottom ? 1 : 0);
    ivec2 opposite = point + ivec2(right ? -503 : 503, bottom ? -89 : 89);
    if (any(lessThan(min(point, opposite), ivec2(0))) || any(greaterThanEqual(max(point, opposite), atlas))) {
        return false;
    }
    ivec4 mark = ivec4(round(texelFetch(Sampler0, point, 0) * 255.0));
    ivec4 other = ivec4(round(texelFetch(Sampler0, opposite, 0) * 255.0));
    return all(equal(mark, ivec4(73, 86, right ? 98 : 95, 1)))
        && all(equal(other, ivec4(73, 86, right ? 95 : 98, 1)));
}

// Paper dialog decorations use VexUIEntrance tints. Ordinary white text avoids the atlas lookup.
vec4 vex_dialog_art_position(vec4 position, vec2 uv) {
    ivec3 rgb = ivec3(round(Color.rgb * 255.0));
    bool tab = rgb.r >= 224 && rgb.r < 240 && rgb.g >= 224 && rgb.b >= 208;
    bool atlasRow = (rgb.r >> 4) == 12 && rgb.g >= 176 && rgb.g < 224 && rgb.b >= 240;
    if (!tab && !atlasRow && (any(lessThan(rgb, ivec3(240))) || all(equal(rgb, ivec3(255))))) return position;
    int payload = ((rgb.r & 15) << 8) | ((rgb.g & 15) << 4) | (rgb.b & 15);
    // Earlier titles used 0xFFFFFD for their static bitmap panorama.
    bool legacyPanorama = all(equal(rgb, ivec3(255, 255, 253)));
    bool opaqueBacking = all(equal(rgb, ivec3(255, 255, 252)));
    bool settled = payload == 4094 || legacyPanorama || opaqueBacking;
    if (!tab && !settled && payload >= 4000) return position;
    ivec2 atlas = textureSize(Sampler0, 0);
    vec2 texel = uv * vec2(atlas);
    // FontTexture.add insets left/top by +0.01 and right/bottom by -0.01 texel.
    // Vertex indices include the staged buffer's draw offset, which can be any phase modulo four.
    bool right = fract(texel.x) > 0.5;
    bool bottom = fract(texel.y) > 0.5;
    ivec4 mark;
    if (atlasRow) {
        int row = (rgb.g >> 4) - 11;
        int slot = row + (payload < 2000 ? 3 : 0);
        // The title contains 2016 equal spaces. The loaded font rewinds by 4032px, another
        // multiple of 2016. An unavailable font adds 31 equal missing glyph advances instead.
        // Removing both multiples isolates the object's 8px right edge.
        int withinLead = int(mod(round(Position.x) - float(8 * slot), 2016.0));
        right = withinLead % 31 == 8;
        // The untransformed glyph straddles the middle of the native dialog's 33px header.
        bottom = Position.y > 16.5;
        if (!vex_dialog_atlas_marker(ivec2(round(texel)), atlas, right, bottom)) return position;
        mark = ivec4(73, 86, right ? 98 : 95, 1);
    } else {
        ivec2 point = clamp(ivec2(floor(texel)), ivec2(0), atlas - 1);
        mark = ivec4(round(texelFetch(Sampler0, point, 0) * 255.0));
        if (mark.r != 73 || mark.g != 86 || mark.a != 1 || mark.b < 91 || mark.b > 98) return position;
    }
    if (tab && mark.b != 93) return position;
    if ((legacyPanorama || opaqueBacking) && mark.b < 95) return position;
    vexOwned = 1.0;

    if (tab) {
        // Left and right start at their previous bounds. A translating icon has zero width delta.
        int motion = ((rgb.b - 208) << 9) | ((rgb.g - 224) << 4) | (rgb.r - 224);
        int startX = ((motion >> 6) & 127) - 64;
        int widthDelta = ((motion >> 13) - 1) * 62;
        float age = mod(GameTime * 24000.0 - float(motion & 63), 64.0);
        if (age > 56.0) age -= 64.0;
        float remaining = 1.0 - clamp(age / 9.0, 0.0, 1.0);
        float spring = remaining * remaining * (2.2 * remaining - 1.2);
        position.x += spring * float(startX + (right ? widthDelta : 0));
        vexAnimatedColor = vec4(1.0, 1.0, 1.0, Color.a);
        return position;
    }
    bool exiting = payload >= 2000 && !settled;
    float age = mod(GameTime * 24000.0 - float(payload % 2000), 2000.0);
    // A staggered island may have a timestamp a few ticks into the future.
    if (age > 1950.0) age -= 2000.0;
    float opacity = 1.0;
    if (mark.b >= 95) {
        float viewport = ceil(2.0 / abs(ProjMat[0][0]) - 0.001);
        float left = floor((viewport - 1008.0) * 0.5);
        float tileEdge = atlasRow ? (right ? 4.0 : 0.0) : float(mark.b - 95) + (right ? 1.0 : 0.0);
        position.x = left + tileEdge * 252.0;
        if (atlasRow) {
            int row = (rgb.g >> 4) - 11;
            // Native object glyphs are eight pixels high and begin 16 pixels below row zero.
            position.y += float(row * 180 - 16) + (bottom ? 172.0 : 0.0);
        }
        bool feather = round(ScreenSize.x / viewport) == 3.0;
        vexProgressionPart = opaqueBacking ? (feather ? -4 : -5) : (feather ? -2 : -3);
        vexProgressionCell = vec2(position.x, viewport);
        vexRewardPoint = vec2(tileEdge, bottom ? 1.0 : 0.0);
        float blend = settled ? 1.0 : smoothstep(0.0, 7.0, age);
        // Both panorama layers use the same progress. The fragment shader needs the
        // outgoing value before opacity is applied, since coverage varies across the edges.
        vexRewardAge = exiting ? blend : -1.0;
        if (!exiting) opacity = blend;
    } else if (!settled) {
        // With island starts two ticks apart, intermediate bridges follow each adjoining
        // endpoint independently, keeping the boardwalk attached as the islands land.
        if (mark.b == 91 || mark.b == 94) age += right ? -1.0 : 1.0;
        if (exiting) {
            // The sender positions only outgoing art offscreen so shaderless clients always
            // show the new state. Its compensating space leaves native hit regions untouched.
            position.x += 4096.0;
            float leave = smoothstep(0.0, 6.0, age);
            position.xy += vec2(-8.0, -4.0) * leave;
            opacity = 1.0 - leave;
        } else {
            float t = clamp(age / 11.0, 0.0, 1.0);
            float spring = pow(1.0 - t, 3.0) * cos(8.7 * t);
            position.xy += vec2(19.0, 12.0) * spring;
            opacity = smoothstep(0.0, 4.5, age);
        }
    }
    // These compact foreground effects keep running after the entrance reaches its final pose.
    if (mark.b == 91) {
        vexProgressionPart = -6;
        vexRewardPoint.x = position.x;
        vexRewardAge = mod(GameTime * 24000.0, 100.0) * 4.0;
    } else if (mark.b == 92) {
        vexProgressionPart = -7;
        vexRewardAge = mod(GameTime * 24000.0, 40.0) * 0.5;
    }
    vexAnimatedColor = vec4(1.0, 1.0, 1.0, opacity * Color.a);
    return position;
}

vec4 vex_screen_ui_position(vec4 position, vec2 uv, int vertexId) {
    int encoded = int(floor(position.y / float(VEX_STRIDE))) - VEX_BASE;
    if (encoded < 0 || encoded >= 819 * VEX_Y_COUNT) return position;

    int group = encoded / VEX_Y_COUNT;
    int kind = group / 9;
    int transition = -1;
    float scale = 1.0;
    bool rounded = kind >= 79 && kind < 85;
    bool pixel = kind >= 85;
    bool sprite = kind >= 73 && kind < 79;
    if (rounded || pixel) {
        scale = float(5 + kind - (rounded ? 79 : 85)) / 10.0;
        kind = 0;
    } else if (sprite) {
        scale = float(5 + kind - 73) / 10.0;
        kind = 0;
    } else if (kind >= 25) {
        transition = (kind - 25) / 6;
        scale = float(5 + (kind - 25) % 6) / 10.0;
        kind = 0;
    } else if (kind >= 9) {
        scale = float(6 + (kind - 9) / 4) / 10.0;
        kind = 5 + (kind - 9) % 4;
    } else if (kind >= 5) scale = 0.5;
    bool panel = kind == 1;
    bool card = kind == 2 || kind == 4 || kind == 6 || kind == 8;
    int anchor = group % 9;
    int localY = encoded % VEX_Y_COUNT + VEX_MIN_Y;
    ivec2 cell = pixel ? ivec2(3, 5) : (panel || card || sprite) ? ivec2(256, 256) : ivec2(16, 14);
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
    if (transition >= 0) {
        ivec3 bytes = ivec3(round(Color.rgb * 255.0));
        int payload = (bytes.r << 16) | (bytes.g << 8) | bytes.b;
        int started = (payload >> 6) & 511;
        int rgb = payload >> 15;
        int offsetX = (payload & 63) - 32;
        // 500 divides the 24000-tick shader clock, including the day boundary.
        float age = mod(GameTime * 24000.0 - float(started) + 500.0, 500.0);
        if (age > 250.0) age -= 500.0; // Short future transport lead: hold the initial state.
        float duration = float(2 << (transition % 4));
        float progress = clamp(age / duration, 0.0, 1.0);
        float opacity = 1.0;
        if (transition < 4) position.x += float(offsetX) * (1.0 - progress);
        else opacity = 1.0 - smoothstep(0.0, 1.0, progress);
        vexAnimatedColor = vec4(vec3((rgb >> 6) & 7, (rgb >> 3) & 7, rgb & 7) / 7.0, opacity);
    }
    if (rounded) {
        ivec3 bytes = ivec3(round(Color.rgb * 255.0));
        int payload = (bytes.r << 16) | (bytes.g << 8) | bytes.b;
        vec2 size = vec2(float((payload >> 15) + 1), float(((payload >> 7) & 255) + 1));
        float radius = round(texelFetch(Sampler0, origin + ivec2(1, 0), 0).r * 255.0);
        vexPanelSize = size;
        vexPanelRadius = min(radius, min(size.x, size.y) * 0.5);
        vexPanelPoint = vec2(right ? size.x : 0.0, bottom ? size.y : 0.0);
        position.x += right ? size.x - 16.0 : 0.0;
        position.y = reference.y + float(localY) + (bottom ? size.y : 0.0);
        vexAnimatedColor = vec4(vec3(0.0), float(payload & 127) / 100.0);
    }
    if (pixel) {
        position.y = reference.y + float(localY) + (bottom ? 3.0 : 0.0);
        vexAnimatedColor = vec4(Color.rgb, 1.0);
        vexSolidPixel = 1.0;
    }
    // Three top lanes reserve 256 logical pixels plus a 16-pixel margin each.
    // The same transform applies to text, icons, avatar pixels and panel backgrounds.
    if (anchor < 3) scale = min(scale, max(0.1, (guiSize.x / 3.0 - 8.0) / 272.0));
    // Explicit scale is independent of window width; all local geometry shares its anchor pivot.
    position.xy = reference + (position.xy - reference) * scale;
    return position;
}
