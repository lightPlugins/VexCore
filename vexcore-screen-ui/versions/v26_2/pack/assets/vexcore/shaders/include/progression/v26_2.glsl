// Marked 18x22 glyphs share a scene pivot and carry relative coordinates in their horizontal offset.
vec4 vex_progression_position(vec4 position, vec2 uv, int vertexId) {
    int part = int(floor((position.y - 98304.0) / 512.0));
    if (part < 0 || part >= 7) return position;
    int corner = vertexId % 4;
    bool bottom = corner == 1 || corner == 2;
    bool right = corner == 2 || corner == 3;
    ivec2 atlas = textureSize(Sampler0, 0);
    ivec2 origin = ivec2(floor(uv * vec2(atlas))) - ivec2(right ? 17 : 0, bottom ? 21 : 0);
    if (any(lessThan(origin, ivec2(0))) || any(greaterThanEqual(origin + ivec2(0, 1), atlas))) return position;
    ivec4 first = ivec4(round(texelFetch(Sampler0, origin, 0) * 255.0));
    ivec4 second = ivec4(round(texelFetch(Sampler0, origin + ivec2(0, 1), 0) * 255.0));
    if (any(notEqual(first, ivec4(86, 88, 49, 1))) || any(notEqual(second, ivec4(67, 79, 82, 1)))) return position;

    vexOwned = 1.0;
    vexProgressionPart = part;
    vexProgressionCell = vec2(right ? 18.0 : 0.0, bottom ? 22.0 : 0.0);
    ivec2 bytes = ivec2(round(Color.gb * 255.0));
    int payload = (bytes.x << 8) | bytes.y;
    int tone = (payload >> 7) & 3;
    float age = mod(GameTime * 24000.0 - float(payload & 127) + 120.0, 120.0);
    if (age > 110.0) age -= 120.0;
    vexRewardAge = age;

    vec2 viewport = ceil(vec2(2.0 / abs(ProjMat[0][0]), 2.0 / abs(ProjMat[1][1])) - 0.001);
    float centered = position.x - floor(viewport.x * 0.5);
    float row = floor((centered + 2048.0) / 4096.0);
    vec2 local = vec2(centered - row * 4096.0, row + vexProgressionCell.y - 2.0);
    float birth = smoothstep(0.0, 2.0, age);
    float exit = smoothstep(88.0, 102.0, age);
    float windup = smoothstep(82.0, 87.0, age) * (1.0 - smoothstep(87.0, 91.0, age));
    float lift = clamp((age - 87.0) / 15.0, 0.0, 1.0);
    float opacity = birth;
    float fracture = max(age - 10.0, 0.0);
    float numberScale = mix(4.65, 2.9, float((payload >> 9) & 1));

    if (part == 1) {
        float pop = 1.0 - 0.59 * exp(-fracture * 0.29) * cos(fracture * 0.97);
        local *= numberScale * pop;
        opacity *= smoothstep(10.0, 12.0, age);
    } else if (part >= 2 && part <= 5) {
        vec2 direction = vec2(part % 2 == 0 ? -1.0 : 1.0, part < 4 ? -1.0 : 1.0);
        float fractureWindup = smoothstep(5.0, 9.8, age) * (1.0 - step(10.0, age));
        local *= numberScale * (1.0 + 0.10 * fractureWindup);
        float spin = direction.x * direction.y * min(0.70, fracture * 0.085);
        local = mat2(cos(spin), sin(spin), -sin(spin), cos(spin)) * local;
        float travel = 1.0 - exp(-fracture * 0.20);
        local += direction * travel * vec2(55.0, 33.0) + vec2(0.0, fracture * fracture * 0.17);
        opacity *= 1.0 - smoothstep(1.0, 12.0, fracture);
    } else if (part == 6) {
        vec2 seed = vec2(local.x - vexProgressionCell.x, row);
        vec2 star = vexProgressionCell - vec2(2.5, 8.5);
        if ((payload & 1024) != 0) {
            int index = (payload >> 11) & 7;
            float twinkleAge = age - (23.0 + float(index) * 3.7);
            float phase = mod(twinkleAge, 33.0);
            float envelope = smoothstep(0.0, 2.0, phase) * (1.0 - smoothstep(2.0, 9.0, phase));
            opacity *= step(0.0, twinkleAge) * envelope * (1.0 - smoothstep(81.0, 87.0, age));
            local = seed + vec2(sin(age * 0.18 + float(index)) * 2.0, -phase * 0.55)
                + star * vec2(0.45 + envelope * 0.65, 0.70 + envelope);
        } else if (age < 82.0) {
            float travel = 1.0 - exp(-fracture * 0.17);
            float streak = 1.0 - smoothstep(0.0, 8.0, fracture);
            vec2 direction = normalize(seed);
            vec2 stretched = star + direction * dot(star, direction) * streak * 2.5;
            local = seed * (0.8 + 4.6 * travel) + stretched * (0.42 + 0.28 * streak)
                + vec2(0.0, fracture * fracture * 0.055);
            opacity *= smoothstep(0.0, 0.8, fracture) * (1.0 - smoothstep(3.0, 21.0, fracture));
        } else {
            float sparkAge = max(age - 86.0 - abs(seed.x) * 0.13, 0.0);
            float spread = 1.0 - exp(-sparkAge * 0.24);
            float curl = seed.y * spread * 2.2;
            float taper = 1.0 - smoothstep(2.0, 12.0, sparkAge);
            local = vec2(seed.x * (2.0 + spread * 3.1) + curl, seed.y * (1.4 + spread))
                + star * vec2(0.45 + 0.25 * taper, 0.55 + 2.25 * taper)
                + vec2(0.0, -sparkAge * (3.0 + abs(seed.y) * 0.22) - sparkAge * sparkAge * 0.25);
            opacity *= smoothstep(0.0, 1.0, sparkAge) * taper;
        }
    }
    vexRewardPoint = local;
    if (part <= 1) {
        // Lift neighboring glyphs in a wave while the fragments dissolve inside each moving cell.
        float glyphX = (centered - row * 4096.0 - vexProgressionCell.x) * (part == 1 ? numberScale : 1.0);
        // The transport row is identical at all four corners, including when the number is scaled.
        float stagger = clamp((age - 87.0 - abs(glyphX) * 0.042 - max(row, 0.0) * 0.045) / 9.0, 0.0, 1.0);
        local += vec2(glyphX * 0.22 + sin(glyphX * 0.27 + row) * 8.0,
            -24.0 - cos(glyphX * 0.18) * 11.0) * stagger * stagger;
        opacity *= 1.0 - max(smoothstep(0.37, 1.0, stagger), exit * exit);
    }

    float entry = clamp(age / 5.5, 0.0, 1.0);
    float remaining = 1.0 - entry;
    float landingAge = max(age - 4.4, 0.0);
    float spring = exp(-landingAge * 0.28) * sin(landingAge * 0.94);
    float recoil = exp(-fracture * 0.39) * sin(fracture * 0.88);
    float drift = smoothstep(13.0, 24.0, age) * sin((age - 13.0) * 0.17) * (1.0 - exit);
    float angle = radians(-17.0 * remaining + 8.0 * spring - 5.0 * recoil + 1.8 * drift
        + 7.0 * windup - 22.0 * lift);
    float tilt = remaining * 0.53 - spring * 0.24 + recoil * 0.20 + drift * 0.10
        + windup * 0.36 - lift * 0.20;
    local *= (0.57 + 0.43 * entry + 0.16 * spring + 0.12 * recoil) * (1.0 + windup * 0.16 - lift * 0.20);
    local *= vec2(1.0, cos(tilt)) / (1.0 + local.x * sin(tilt) / 170.0);
    local = mat2(cos(angle), sin(angle), -sin(angle), cos(angle)) * local;
    float scale = min(1.12, min(viewport.x / 242.0, viewport.y / 145.0));
    position.xy = viewport * 0.5 + local * scale
        + vec2(0.0, -(viewport.y * 0.5 + 48.0) * remaining * remaining
            + 19.0 * spring - 7.0 * recoil + 2.0 * drift + (9.0 * windup - 58.0 * lift * lift) * scale);
    vec3 rgb = tone == 0 ? vec3(0.92, 0.88, 1.0)
        : tone == 1 ? vec3(1.0, 0.78, 0.40) : vec3(0.81, 0.73, 0.92);
    if (part == 6 && age >= 86.0) {
        rgb = mix(vec3(1.0, 0.93, 0.63), vec3(0.67, 0.40, 1.0), lift);
    }
    vexAnimatedColor = vec4(rgb, opacity);
    return position;
}
