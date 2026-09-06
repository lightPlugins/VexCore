package dev.vexsoft.core.paper.screenui;

import lombok.Builder;
/** Position and drawing order of a pack-backed image. */
@Builder
public record TextureLayout(int x, int y, HorizontalAlignment horizontalAlignment,
VerticalAlignment verticalAlignment, int layer, ScreenAnchor anchor,
net.kyori.adventure.text.format.TextColor tint, UiAnimation animation, boolean compact, double scale) {
  public TextureLayout(int x, int y, HorizontalAlignment horizontalAlignment,
      VerticalAlignment verticalAlignment, int layer, ScreenAnchor anchor,
      net.kyori.adventure.text.format.TextColor tint, UiAnimation animation) {
    this(x, y, horizontalAlignment, verticalAlignment, layer, anchor, tint, animation, false);
  }
  public TextureLayout(int x, int y, HorizontalAlignment horizontalAlignment,
      VerticalAlignment verticalAlignment, int layer, ScreenAnchor anchor) {
    this(x, y, horizontalAlignment, verticalAlignment, layer, anchor, null, null);
  }
  /** Preserves the original bossbar-relative constructor. */
  public TextureLayout(int x, int y, HorizontalAlignment horizontalAlignment,
      VerticalAlignment verticalAlignment, int layer) {
    this(x, y, horizontalAlignment, verticalAlignment, layer, null);
  }

    public TextureLayout(int x, int y, HorizontalAlignment horizontalAlignment,
      VerticalAlignment verticalAlignment, int layer, ScreenAnchor anchor,
      net.kyori.adventure.text.format.TextColor tint, UiAnimation animation, boolean compact) {
    this(x, y, horizontalAlignment, verticalAlignment, layer, anchor, tint, animation, compact, 0);
  }
  public TextureLayout {
    scale = UiScale.resolve(scale, compact);
    if ((animation != null || compact || scale != 1.0) && anchor == null) throw new IllegalArgumentException("Animations/compact UI require a screen anchor");
    if (tint == null) tint = net.kyori.adventure.text.format.NamedTextColor.WHITE;
    if (Math.abs((long) x) > 512 || (anchor == null ? y < 0 || y > 240 : y < -256 || y > 256)) {
      throw new IllegalArgumentException("Anchor outside supported UI range");
    }

    if (horizontalAlignment == null) {
      horizontalAlignment = HorizontalAlignment.LEFT;
    }

    if (verticalAlignment == null) {
      verticalAlignment = VerticalAlignment.TOP;
    }

  }

}
