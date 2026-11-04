package dev.vexsoft.core.paper.vexui.template;

import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import dev.vexsoft.core.paper.vexui.element.VexUIItemIcon;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/** Size-aware item or custom-font icon supplied by the owning plugin. */
@FunctionalInterface
public interface VexUIIcon {

    /** Creates an icon at a supported logical size without mutating the source definition. */
    VexUIElement at(int pixels);

    /** References an already registered vanilla flat sprite. */
    static VexUIIcon item(final String sprite) {
        Key key = Key.key(sprite);
        return pixels -> new VexUIItemIcon(key, pixels, Component.empty());
    }
}
