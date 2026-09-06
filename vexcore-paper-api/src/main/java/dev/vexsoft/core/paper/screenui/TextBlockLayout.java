package dev.vexsoft.core.paper.screenui;

import lombok.Builder;
/** GUI pixels relative to the center of the bossbar title; positive Y points down. */
@Builder
public record TextBlockLayout(int x, int y, int maxWidth, int lineSpacing,
HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment,
TextOverflow overflow, int layer, ScreenAnchor anchor, UiAnimation animation, boolean compact, double scale) {
  public TextBlockLayout(int x, int y, int maxWidth, int lineSpacing,
      HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment,
      TextOverflow overflow, int layer, ScreenAnchor anchor, UiAnimation animation) {
    this(x, y, maxWidth, lineSpacing, horizontalAlignment, verticalAlignment, overflow, layer, anchor, animation, false);
  }
  public TextBlockLayout(int x, int y, int maxWidth, int lineSpacing,
      HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment,
      TextOverflow overflow, int layer, ScreenAnchor anchor) {
    this(x, y, maxWidth, lineSpacing, horizontalAlignment, verticalAlignment, overflow, layer, anchor, null);
  }
  /** Preserves the original bossbar-relative constructor. Null anchor selects that mode. */
  public TextBlockLayout(int x, int y, int maxWidth, int lineSpacing,
      HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment,
      TextOverflow overflow, int layer) {
    this(x, y, maxWidth, lineSpacing, horizontalAlignment, verticalAlignment, overflow, layer, null);
  }

    public TextBlockLayout(int x, int y, int maxWidth, int lineSpacing,
      HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment,
      TextOverflow overflow, int layer, ScreenAnchor anchor, UiAnimation animation, boolean compact) {
    this(x, y, maxWidth, lineSpacing, horizontalAlignment, verticalAlignment, overflow, layer, anchor, animation, compact, 0);
  }
  public TextBlockLayout {
    scale = UiScale.resolve(scale, compact);
    if ((animation != null || compact || scale != 1.0) && anchor == null) throw new IllegalArgumentException("Animations/compact UI require a screen anchor");
    if (Math.abs((long) x) > 512 || (anchor == null ? y < 0 || y > 240 : y < -256 || y > 256)) {
      throw new IllegalArgumentException("Anchor outside supported UI range");
    }

    if (maxWidth == 0) {
      maxWidth = 240;
    }

    if (maxWidth < 8 || maxWidth > 512 || lineSpacing < 0 || lineSpacing > 32) {
      throw new IllegalArgumentException("Invalid text width or line spacing");
    }

    if (horizontalAlignment == null) {
      horizontalAlignment = HorizontalAlignment.LEFT;
    }

    if (verticalAlignment == null) {
      verticalAlignment = VerticalAlignment.TOP;
    }

    if (overflow == null) {
      overflow = TextOverflow.WRAP;
    }

  }

}
