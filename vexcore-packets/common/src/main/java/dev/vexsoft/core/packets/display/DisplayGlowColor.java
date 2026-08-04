package dev.vexsoft.core.packets.display;

import java.util.Locale;
import java.util.Objects;
import lombok.Value;

@Value
public class DisplayGlowColor {
  int rgb;

  public DisplayGlowColor(final int rgb) {
    if ((rgb & 0xFF000000) != 0) {
      throw new IllegalArgumentException("rgb must be a 24-bit color");
    }
    this.rgb = rgb;
  }

  public static DisplayGlowColor of(final int rgb) {
    return new DisplayGlowColor(rgb);
  }

  public static DisplayGlowColor hex(final String hex) {
    String normalized = Objects.requireNonNull(hex, "hex").trim();
    if (normalized.startsWith("#")) {
      normalized = normalized.substring(1);
    }
    if (normalized.length() != 6) {
      throw new IllegalArgumentException("hex color must use the #rrggbb format");
    }
    try {
      return new DisplayGlowColor(Integer.parseInt(normalized, 16));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("hex color must use the #rrggbb format", exception);
    }
  }

  public String getHex() {
    return "#" + String.format(Locale.ROOT, "%06x", rgb);
  }
}
