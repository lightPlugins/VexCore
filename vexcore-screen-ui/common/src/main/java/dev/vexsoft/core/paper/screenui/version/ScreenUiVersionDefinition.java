package dev.vexsoft.core.paper.screenui.version;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.screenui.ScreenAnchor;
import dev.vexsoft.core.paper.screenui.UiTexture;
import java.util.Set;
import net.kyori.adventure.key.Key;

/** Selects the font/shader protocol matching a client resource-pack revision. */
public interface ScreenUiVersionDefinition extends VexService {
  String getAdapterVersion();

  Set<String> getSupportedVersions();

  Key textFont(ScreenAnchor anchor, int y);

  Key textureFont(UiTexture texture, ScreenAnchor anchor, int y);
  default Key textFont(ScreenAnchor anchor, int y, boolean animated, double scale) {
    if (scale != 1.0 && scale != 0.5) throw new UnsupportedOperationException("UI scale not supported");
    return textFont(anchor, y, animated, scale == 0.5);
  }
  default Key textureFont(UiTexture texture, ScreenAnchor anchor, int y, boolean animated, double scale) {
    if (scale != 1.0 && scale != 0.5) throw new UnsupportedOperationException("UI scale not supported");
    return textureFont(texture, anchor, y, animated, scale == 0.5);
  }
  default Key iconFont(Key source, ScreenAnchor anchor, int y, boolean animated, double scale) {
    if (scale != 1.0 && scale != 0.5) throw new UnsupportedOperationException("UI scale not supported");
    return iconFont(source, anchor, y, animated, scale == 0.5);
  }
  default Key textFont(ScreenAnchor anchor, int y, boolean animated, boolean compact) {
    if (compact) throw new UnsupportedOperationException("Compact UI not supported");
    return animated ? animatedTextFont(anchor, y) : textFont(anchor, y);
  }
  default Key textureFont(UiTexture texture, ScreenAnchor anchor, int y, boolean animated, boolean compact) {
    if (compact) throw new UnsupportedOperationException("Compact UI not supported");
    return animated ? animatedTextureFont(texture, anchor, y) : textureFont(texture, anchor, y);
  }
  default Key iconFont(Key source, ScreenAnchor anchor, int y, boolean animated, boolean compact) {
    if (compact) throw new UnsupportedOperationException("Compact UI not supported");
    return iconFont(source, anchor, y, animated);
  }

  default Key animatedTextFont(ScreenAnchor anchor, int y) {
    throw new UnsupportedOperationException("This UI version does not support animations");
  }
  /** Pack contributors supply a marked 16x14 glyph with advance 13, under this convention. */
  default Key iconFont(Key source, ScreenAnchor anchor, int y, boolean animated) {
    throw new UnsupportedOperationException("This UI version does not support contributed icons");
  }
  default Key animatedTextureFont(UiTexture texture, ScreenAnchor anchor, int y) {
    throw new UnsupportedOperationException("This UI version does not support animations");
  }

  /** Reversible horizontal transport decoded by the matching shader; does not change visible X. */
  default int horizontalTransport(int y) {
    return 0;
  }
}
