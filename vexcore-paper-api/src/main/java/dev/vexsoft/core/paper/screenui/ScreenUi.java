package dev.vexsoft.core.paper.screenui;

import java.util.List;
import net.kyori.adventure.text.Component;

/** Owner-scoped screen handle. Updates are copied, validated and coalesced before display. */
public interface ScreenUi extends AutoCloseable {

    /** Measures a panel without changing screen contents, for admission and placement decisions. */
    default UiPanelBounds measurePanel(UiNode content, UiPanelLayout layout) {
        throw new UnsupportedOperationException("Panel measurement is not implemented by this screen");
    }

    /** Atomically measures and replaces a content-sized panel, returning its final slot coordinates. */
    default UiPanelBounds panel(String id, UiNode content, UiPanelLayout layout) {
        throw new UnsupportedOperationException("Panel rendering is not implemented by this screen");
    }

    /** Draws a rounded background using the same renderer as content-sized panels. */
    default void panelBackground(String id, int width, int height, UiPanelLayout layout) {
        throw new UnsupportedOperationException("Panel rendering is not implemented by this screen");
    }

    /** Creates or replaces a text block with the supplied lines and layout. */
    void textBlock(String id, List<Component> lines, TextBlockLayout layout);

    /** Creates or replaces a texture block at the specified layout. */
    void textureBlock(String id, UiTexture texture, TextureLayout layout);

    /** Replaces the contents of an existing text block. */
    void setLines(String id, List<Component> lines);

    /** Removes the identified visual and releases its runtime state. */
    void remove(String id);

    /** Replaces one bounded card. Components retain their individual colors and bold style. */
    default void box(String id, List<Component> lines, UiBoxLayout box) {
        UiTexture background = UiTexture.card(box.height());
        int left = box.x() - (box.anchor().ordinal() % 3) * (background.width() / 2);
        int width = background.width() - box.padding() * 2;

        if (lines.size() * (12 + box.lineSpacing()) - box.lineSpacing() > box.height() - 2 * box.padding()) {
            throw new IllegalArgumentException("Card content exceeds its height");
        }

        var text = TextBlockLayout.builder()
            .anchor(box.anchor())
            .x(left + box.padding() + switch (box.alignment()) {
                case LEFT -> 0;
                case CENTER -> width / 2;
                case RIGHT -> width;
            })
            .y(box.y() + box.padding())
            .maxWidth(width)
            .lineSpacing(box.lineSpacing())
            .horizontalAlignment(box.alignment())
            .overflow(TextOverflow.ELLIPSIS)
            .layer(box.layer() + 1)
            .animation(box.animation())
            .scale(box.scale())
            .build();
        // Validate the whole description before the first update.
        var texture = TextureLayout.builder()
            .anchor(box.anchor())
            .x(left)
            .y(box.y())
            .layer(box.layer())
            .tint(box.tint())
            .animation(box.animation())
            .scale(box.scale())
            .build();

        if (box.background()) {
            textureBlock(id + ".background", background, texture);
        } else {
            remove(id + ".background");
        }

        textBlock(id + ".text", lines, text);
    }

    /** Removes the background and text elements of a composed box. */
    default void removeBox(String id) {
        remove(id + ".background");
        remove(id + ".text");
    }

    /** Returns whether this presentation has been retired. */
    boolean isClosed();

    /** Removes this screen and its elements from the player's UI session. */
    @Override
    void close();
}
