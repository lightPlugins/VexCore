package dev.vexsoft.core.paper.screenui;

import java.util.List;
import net.kyori.adventure.text.Component;

/** Owner-scoped screen handle. Updates are copied, validated and coalesced before display. */
public interface ScreenUi extends AutoCloseable {
  void textBlock(String id, List<Component> lines, TextBlockLayout layout);
  void textureBlock(String id, UiTexture texture, TextureLayout layout);
  void setLines(String id, List<Component> lines);
  void remove(String id);
  /** Replaces one bounded card. Components retain their individual colors and bold style. */
  default void box(String id, List<Component> lines, UiBoxLayout box) {
    UiTexture background = UiTexture.card(box.height());
    int left = box.x() - (box.anchor().ordinal() % 3) * (background.width() / 2);
    int width = background.width() - box.padding() * 2;
    if (lines.size() * (12 + box.lineSpacing()) - box.lineSpacing() > box.height() - 2 * box.padding()) {
      throw new IllegalArgumentException("Card content exceeds its height");
    }
    var text = TextBlockLayout.builder().anchor(box.anchor()).x(left + box.padding()
        + switch (box.alignment()) { case LEFT -> 0; case CENTER -> width / 2; case RIGHT -> width; })
        .y(box.y() + box.padding()).maxWidth(width).lineSpacing(box.lineSpacing())
        .horizontalAlignment(box.alignment()).overflow(TextOverflow.ELLIPSIS)
        .layer(box.layer() + 1).animation(box.animation()).scale(box.scale()).build();
    // Validate the whole description before the first update.
    var texture = TextureLayout.builder().anchor(box.anchor()).x(left).y(box.y())
        .layer(box.layer()).tint(box.tint()).animation(box.animation()).scale(box.scale()).build();
    if (box.background()) textureBlock(id + ".background", background, texture);
    else remove(id + ".background");
    textBlock(id + ".text", lines, text);
  }
  default void removeBox(String id) {
    remove(id + ".background");
    remove(id + ".text");
  }
  boolean isClosed();
  @Override void close();
}
