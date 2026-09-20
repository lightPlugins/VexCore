flat in int interactiveKind;
flat in vec4 interactiveColor;
flat in vec2 interactiveSize;
in vec2 interactivePixel;

vec4 interactive_ui_fragment() {
    if (interactiveKind == 1) {
        vec2 nearestEdge = min(interactivePixel, interactiveSize - interactivePixel);
        float edge = min(nearestEdge.x, nearestEdge.y);
        vec3 tint = interactiveColor.rgb + (edge < 1.0 ? vec3(0.09) : vec3(0.0));
        return vec4(clamp(tint, 0.0, 1.0), interactiveColor.a);
    }
    // A crisp arrow with a dark outline; no externally sourced bitmap is needed.
    vec2 pixel = interactivePixel;
    bool arrow = pixel.y < 9.0 && pixel.x < pixel.y * 0.65 + 1.0;
    bool tail = pixel.y >= 7.0 && pixel.x >= 2.0 && pixel.x < 4.0;
    if (!arrow && !tail) discard;
    bool outline = pixel.x < 1.0 || (arrow && pixel.x > pixel.y * 0.65 - 0.1)
        || (tail && (pixel.x < 2.6 || pixel.x > 3.4 || pixel.y > 11.0));
    return outline ? vec4(0.05, 0.07, 0.10, 1.0) : vec4(0.97, 0.98, 1.0, 1.0);
}
