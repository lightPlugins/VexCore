package dev.vexsoft.core.paper.vexui.render;

import dev.vexsoft.core.paper.vexui.layout.VexUIBounds;
import java.util.List;
import net.kyori.adventure.text.Component;

/** Dialog body plus measured hit regions for diagnostics and geometric acceptance tests. */
public record VexUIRenderedFrame(Component body, int width, int height, List<HitRegion> regions) {

    public VexUIRenderedFrame {
        regions = List.copyOf(regions);
    }

    public record HitRegion(VexUIBounds bounds, String action) {
    }
}
