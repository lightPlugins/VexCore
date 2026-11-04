package dev.vexsoft.core.paper.vexui.layout;

import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import java.util.List;

/** Fully measured tree shared by rendering and geometric tests. */
public record VexUIPlacement(VexUIElement element, VexUIBounds bounds, List<VexUIPlacement> children) {

    public VexUIPlacement {
        children = List.copyOf(children);
    }
}
