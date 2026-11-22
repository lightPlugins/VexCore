package dev.vexsoft.core.paper.vexui.element;

import java.util.List;
import java.util.Objects;

/** A fixed-size map of independently positioned elements, sharing the renderer's background and text passes. */
public record VexUIMap(VexUISize size, List<Layer> layers) implements VexUIElement {

    public VexUIMap {
        if (size.width() < 1 || size.height() < 1) {
            throw new IllegalArgumentException("Map dimensions must be positive");
        }
        layers = List.copyOf(layers);
        for (var layer : layers) {
            if (layer.x() > size.width() - layer.width() || layer.y() > size.height() - layer.height()) {
                throw new IllegalArgumentException("Layer outside map bounds");
            }
        }
    }

    /** Positions a visual or native interaction region independently of surrounding layers. */
    public record Layer(int x, int y, int width, int height, VexUIElement element) {
        public Layer {
            Objects.requireNonNull(element, "element");
            if (x < 0 || y < 0 || width < 1 || height < 1) {
                throw new IllegalArgumentException("Invalid map layer bounds");
            }
        }
    }
}
