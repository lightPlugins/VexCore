package dev.vexsoft.core.paper.screenui;

import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Immutable content tree. Plugins supply data; VexCore measures, wraps and arranges it. */
public sealed interface UiNode {

    /** Returns the stable identifier used to expose the node's measured bounds. */
    String id();

    /** A styled paragraph list wrapped using the shared bitmap-font metrics. */
    record Text(String id, List<Component> lines) implements UiNode {

        /** Copies the component list and requires a stable node identifier. */
        public Text {
            Objects.requireNonNull(id);
            lines = List.copyOf(lines);
        }

        /** Creates a text node containing one paragraph. */
        public Text(String id, Component text) {
            this(id, List.of(text));
        }
    }

    /** Ordered horizontal or vertical children with spacing from the panel style. */
    record Group(String id, boolean horizontal, List<UiNode> children) implements UiNode {

        /** Copies children to keep each submitted tree immutable. */
        public Group {
            Objects.requireNonNull(id);
            children = List.copyOf(children);
        }
    }

    /** Reserves space for a plugin-owned visualization without owning its gameplay logic. */
    record Space(String id, int width, int height) implements UiNode {

        /** Rejects reservations outside the supported logical dimensions. */
        public Space {
            Objects.requireNonNull(id);
            if (width < 0 || width > 240 || height < 0 || height > 240) {
                throw new IllegalArgumentException("Invalid panel space");
            }
        }
    }

    /** Opaque 8-by-8 face pixels, composed with the skin's hat layer before rendering. */
    record Avatar(String id, List<Integer> pixels) implements UiNode {

        /** Copies and validates the fixed-size face raster. */
        public Avatar {
            Objects.requireNonNull(id);
            pixels = List.copyOf(pixels);
            if (pixels.size() != 64) {
                throw new IllegalArgumentException("Avatars require exactly 64 RGB pixels");
            }
        }
    }
}
