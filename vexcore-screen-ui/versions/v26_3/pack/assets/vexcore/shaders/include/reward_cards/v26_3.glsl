// Protocol 10. Every primitive shares its clocks, stack movement and card-center pivot.
vec4 vex_reward_card_position(vec4 position, vec2 uv, int vertexId) {
    int carrier = int(floor((position.y - 65536.0) / 512.0));
    if (carrier < 0 || carrier >= 64) return position;
    int primitive = carrier % 4;
    int slot = carrier / 16;
    int previousSlot = (carrier / 4) % 4;
    ivec2 cell = primitive == 1 ? ivec2(224, 42) : primitive == 2 ? ivec2(34, 34) : ivec2(18, 22);
    int corner = vertexId % 4;
    bool bottom = corner == 1 || corner == 2;
    bool right = corner == 2 || corner == 3;
    ivec2 atlas = textureSize(Sampler0, 0);
    ivec2 origin = ivec2(floor(uv * vec2(atlas)))
        - ivec2(right ? cell.x - 1 : 0, bottom ? cell.y - 1 : 0);
    if (any(lessThan(origin, ivec2(0))) || any(greaterThanEqual(origin + ivec2(0, 1), atlas))) return position;
    ivec4 first = ivec4(round(texelFetch(Sampler0, origin, 0) * 255.0));
    ivec4 second = ivec4(round(texelFetch(Sampler0, origin + ivec2(0, 1), 0) * 255.0));
    if (any(notEqual(first, ivec4(86, 88, 49, 1))) || any(notEqual(second, ivec4(67, 79, 82, 1)))) return position;
    vexOwned = 1.0;

    ivec3 bytes = ivec3(round(Color.rgb * 255.0));
    int payload = (bytes.r << 16) | (bytes.g << 8) | bytes.b;
    int tint = payload & 511;
    float clock = mod(GameTime * 24000.0, 120.0);
    float age = mod(clock - float((payload >> 9) & 127) + 120.0, 120.0);
    float motionAge = mod(clock - float((payload >> 16) & 127) + 120.0, 120.0);
    if (age > 110.0) age -= 120.0;
    if (motionAge > 110.0) motionAge -= 120.0;
    bool pulse = (payload & 8388608) != 0;
    float progress = clamp(age / 9.0, 0.0, 1.0);
    float remaining = 1.0 - progress;
    // A damped spring overshoots the resting position, then settles exactly with zero final velocity.
    float eased = 1.0 - pow(remaining, 4.0) * cos(progress * 9.42477796);
    // Start small and let the size spring follow the slide, so the growth is visible as the card lands.
    float popProgress = clamp((age - 1.5) / 7.5, 0.0, 1.0);
    float popRemaining = 1.0 - popProgress;
    float entryPop = 1.0 - 0.16 * pow(popRemaining, 4.0) * cos(popProgress * 9.42477796)
        + 0.12 * sin(popProgress * 9.42477796) * popRemaining * popRemaining;
    float exitProgress = clamp((age - 61.0) / 12.0, 0.0, 1.0);
    // Wind up toward the screen before releasing outward; keep the first rebound fully visible.
    float exitMotion = exitProgress * exitProgress * (3.8 * exitProgress - 2.8);
    float exitBounce = sin(exitProgress * 9.42477796) * pow(1.0 - exitProgress, 2.0);
    float exitFade = smoothstep(0.3, 1.0, exitProgress);
    float opacity = smoothstep(0.0, 2.5, age) * (1.0 - exitFade);
    float reflow = smoothstep(0.0, 6.0, motionAge);

    vec2 viewport = ceil(vec2(2.0 / abs(ProjMat[0][0]), 2.0 / abs(ProjMat[1][1])) - 0.001);
    float centered = position.x - floor(viewport.x * 0.5);
    int transportedY = int(floor((centered + 2048.0) / 4096.0));
    vec2 local = vec2(centered - float(transportedY) * 4096.0,
        float(transportedY) + (bottom ? float(cell.y) : 0.0) - 2.0);
    vexRewardPoint = local;
    vexRewardFrame = primitive == 1 ? 1.0 : 0.0;
    vexRewardAge = pulse ? motionAge + 3.0 : age;
    if (primitive == 3) {
        float p = clamp(motionAge / 6.0, 0.0, 1.0);
        float amountScale = 1.5 * (1.0 + (pulse ? 0.50 * sin(p * 3.14159265) : 0.0));
        // Scale the whole quantity around its right edge, preserving alignment even for compact suffixes.
        local = vec2(216.0, 20.0) + (local - vec2(216.0, 20.0)) * amountScale;
    }
    float angle = radians(13.0) * (1.0 - eased) - radians(13.0) * exitFade + radians(4.0) * exitBounce;
    float cardPop = entryPop + 0.14 * exitBounce;
    vec2 centeredCard = (local - vec2(112.0, 20.0)) * cardPop;
    local = mat2(cos(angle), sin(angle), -sin(angle), cos(angle)) * centeredCard + vec2(112.0, 20.0);
    local.x += 260.0 * (1.0 - eased) + 96.0 * exitMotion;
    local.y += 12.0 * exitMotion;
    local.y += -float(slot) * 44.0 + float(slot - previousSlot) * 44.0 * (1.0 - reflow);
    // A narrow window shrinks the whole card and pivot consistently, without a viewport packet.
    float scale = min(0.7, max(0.1, (viewport.x - 24.0) / 224.0));
    position.xy = viewport + vec2(-12.0, -12.0) + (local - vec2(224.0, 40.0)) * scale;
    vec3 rgb = primitive == 1 || primitive == 2 ? vec3(1.0)
        : vec3((tint >> 6) & 7, (tint >> 3) & 7, tint & 7) / 7.0;
    vexAnimatedColor = vec4(rgb, opacity);
    return position;
}
