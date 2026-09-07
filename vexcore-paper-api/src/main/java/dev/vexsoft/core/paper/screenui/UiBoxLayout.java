package dev.vexsoft.core.paper.screenui;

import lombok.Builder;
import net.kyori.adventure.text.format.TextColor;

/** A 240 px card (160 px for a 20 px toast), with an anchor-selected pivot. Y is its top edge. */
@Builder
public record UiBoxLayout(
    ScreenAnchor anchor,
    int x,
    int y,
    int height,
    int padding,
    int lineSpacing,
    HorizontalAlignment alignment,
    TextColor tint,
    boolean background,
    int layer,
    UiAnimation animation,
    boolean compact,
    double scale) {
  /** Creates the layout using the supplied options and defaults for omitted settings. */
  public UiBoxLayout(
      ScreenAnchor anchor,
      int x,
      int y,
      int height,
      int padding,
      int lineSpacing,
      HorizontalAlignment alignment,
      TextColor tint,
      boolean background,
      int layer,
      UiAnimation animation,
      boolean compact) {
    this(
        anchor,
        x,
        y,
        height,
        padding,
        lineSpacing,
        alignment,
        tint,
        background,
        layer,
        animation,
        compact,
        0);
  }

  /** Normalizes defaults and validates the configured layout values. */
  public UiBoxLayout {
    scale = UiScale.resolve(scale, compact);
    if (anchor == null) {
      anchor = ScreenAnchor.TOP_RIGHT;
    }
    UiTexture.card(height);
    if (padding < 0 || padding > 32 || lineSpacing < 0 || lineSpacing > 16) {
      throw new IllegalArgumentException("Invalid card padding");
    }
    if (alignment == null) {
      alignment = HorizontalAlignment.LEFT;
    }
  }
}
