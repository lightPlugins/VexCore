// Interactive UI protocol 1. An independent dispatcher precedes the existing screen UI dispatcher.
flat out int interactiveKind;
flat out vec4 interactiveColor;
flat out vec2 interactiveSize;
out vec2 interactivePixel;

vec4 interactive_ui_position(vec4 position, vec2 uv, int vertexId) {
    interactiveKind = -1;
    interactiveColor = vec4(1.0);
    interactiveSize = vec2(1.0);
    interactivePixel = vec2(0.0);
    int kind = int(floor(position.y / 4096.0)) - 512;
    if (kind < 0 || kind > 2) return position;
    int corner = vertexId % 4;
    bool bottom = corner == 1 || corner == 2;
    bool right = corner == 2 || corner == 3;
    ivec2 cell = kind == 0 ? ivec2(6, 10) : ivec2(4, 4);
    ivec2 atlasSize = textureSize(Sampler0, 0);
    ivec2 origin = ivec2(floor(uv * vec2(atlasSize)))
        - ivec2(right ? cell.x - 1 : 0, bottom ? cell.y - 1 : 0);
    if (any(lessThan(origin, ivec2(0))) || any(greaterThanEqual(origin + cell, atlasSize + 1))) return position;
    ivec4 signatureA = ivec4(round(texelFetch(Sampler0, origin, 0) * 255.0));
    ivec4 signatureB = ivec4(round(texelFetch(Sampler0, origin + ivec2(0, 1), 0) * 255.0));
    if (any(notEqual(signatureA, ivec4(86, 73, 85, 1)))
        || any(notEqual(signatureB, ivec4(67, 79, 82, 1)))) return position;
    interactiveKind = kind;
    vec2 guiSize = ceil(vec2(2.0 / abs(ProjMat[0][0]), 2.0 / abs(ProjMat[1][1])) - 0.001);
    vec2 center = floor(guiSize * 0.5);
    float carrierX = position.x - center.x - (right ? float(cell.x) : 0.0);
    float transportedY = floor((carrierX + 512.0) / 1024.0);
    vec2 local = vec2(carrierX - transportedY * 1024.0, transportedY * 0.25 - 90.0);
    if (kind == 0) {
        local += vec2(right ? 6.0 : 0.0, (bottom ? 10.0 : 0.0) - 2.0);
    } else if (kind == 1) {
        ivec3 bytes = ivec3(round(Color.rgb * 255.0));
        int payload = (bytes.r << 16) | (bytes.g << 8) | bytes.b;
        interactiveSize = vec2(float(payload & 511), float((payload >> 9) & 255));
        interactiveColor = vec4(texelFetch(Sampler0, origin + ivec2(1, 0), 0).rgb,
            float((payload >> 17) & 127) / 127.0);
        interactivePixel = vec2(right ? interactiveSize.x : 0.0, bottom ? interactiveSize.y : 0.0);
        local += interactivePixel;
    } else {
        interactiveSize = vec2(8.0, 12.0);
        interactivePixel = vec2(right ? 8.0 : 0.0, bottom ? 12.0 : 0.0);
        local += interactivePixel;
    }
    float scale = min(1.0, min((guiSize.x - 16.0) / 320.0, (guiSize.y - 16.0) / 180.0));
    position.xy = center + local * max(0.25, scale);
    return position;
}
