package dev.vexsoft.core.paper.screenui.v26_2;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.screenui.ScreenAnchor;
import dev.vexsoft.core.paper.screenui.UiTexture;
import dev.vexsoft.core.paper.screenui.version.ScreenUiVersionDefinition;
import java.util.Locale;
import java.util.Properties;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import net.kyori.adventure.key.Key;

/** Minecraft 26.2, resource-pack format 88.0, VexCore shader protocol 6. */
@Dependencies
public final class V26_2ScreenUiVersionDefinition implements ScreenUiVersionDefinition {
  @Override public Key textFont(ScreenAnchor anchor, int y, boolean animated, double scale) {
    return font(scalePrefix(scale) + (animated ? "toast_text" : "text"), anchor, y);
  }
  @Override public Key textureFont(UiTexture texture, ScreenAnchor anchor, int y, boolean animated, double scale) {
    String prefix = scalePrefix(scale);
    if (scale == 1.0) return textureFont(texture, anchor, y, animated, false);
    if (!texture.equals(UiTexture.card(texture.height())) || animated && texture.height() != 20) {
      throw new IllegalArgumentException("Scaled textures require matching card metrics");
    }
    return font(prefix + (animated ? "toast_card" : "card_" + texture.height()), anchor, y);
  }
  @Override public Key iconFont(Key source, ScreenAnchor anchor, int y, boolean animated, double scale) {
    horizontalTransport(y);
    if (!source.value().startsWith("icon/")) throw new IllegalArgumentException("Invalid icon font");
    return Key.key(source.namespace(), "ui/v26_2/" + source.value() + "/" + scalePrefix(scale)
        + (animated ? "toast_" : "") + anchor.name().toLowerCase(Locale.ROOT));
  }
  private static String scalePrefix(double scale) {
    dev.vexsoft.core.paper.screenui.UiScale.validate(scale);
    return scale == 1.0 ? "" : scale == 0.5 ? "compact_" : "scale_" + Math.round(scale * 100) + "_";
  }
  private static final Properties PROTOCOL = loadProtocol();
  public V26_2ScreenUiVersionDefinition() {
  }

  public V26_2ScreenUiVersionDefinition(VexServiceRegistry services) {
  }

  @Override public String getAdapterVersion() {
    return "v26_2";
  }

  @Override public Set<String> getSupportedVersions() {
    return Set.of("26.2");
  }

  @Override public Key textFont(ScreenAnchor anchor, int y) {
    return font("text", anchor, y);
  }

  @Override public int horizontalTransport(int y) {
    if (y < -256 || y > 256) {
      throw new IllegalArgumentException("Anchored glyph origin must be within -256..256 GUI pixels");
    }
    return y * 4096;
  }

  @Override public Key textureFont(UiTexture texture, ScreenAnchor anchor, int y) {
    if (texture.equals(UiTexture.card(texture.height()))) {
      return font("card_" + texture.height(), anchor, y);
    }
    if (!UiTexture.PANEL.equals(texture)) {
      throw new IllegalArgumentException("Anchored textures currently require UiTexture.PANEL");
    }
    return font("panel", anchor, y);
  }

  @Override public Key animatedTextFont(ScreenAnchor anchor, int y) {
    return font("toast_text", anchor, y);
  }
  @Override public Key textFont(ScreenAnchor anchor, int y, boolean animated, boolean compact) {
    return font((compact ? "compact_" : "") + (animated ? "toast_text" : "text"), anchor, y);
  }
  @Override public Key textureFont(UiTexture texture, ScreenAnchor anchor, int y, boolean animated, boolean compact) {
    if (!compact) return animated ? animatedTextureFont(texture, anchor, y) : textureFont(texture, anchor, y);
    if (!texture.equals(UiTexture.card(texture.height()))) throw new IllegalArgumentException("Compact textures require matching card metrics");
    if (animated && texture.height() != 20) throw new IllegalArgumentException("Animated texture requires toast card");
    return font("compact_" + (animated ? "toast_card" : "card_" + texture.height()), anchor, y);
  }
  @Override public Key iconFont(Key source, ScreenAnchor anchor, int y, boolean animated, boolean compact) {
    if (!compact) return iconFont(source, anchor, y, animated);
    if (!source.value().startsWith("icon/")) throw new IllegalArgumentException("Invalid icon font");
    horizontalTransport(y);
    return Key.key(source.namespace(), "ui/v26_2/" + source.value() + "/compact_"
        + (animated ? "toast_" : "") + anchor.name().toLowerCase(Locale.ROOT));
  }

  @Override public Key iconFont(Key source, ScreenAnchor anchor, int y, boolean animated) {
    horizontalTransport(y);
    if (!source.value().startsWith("icon/")) throw new IllegalArgumentException("Invalid icon font");
    return Key.key(source.namespace(), "ui/v26_2/" + source.value() + "/"
        + (animated ? "toast_" : "") + anchor.name().toLowerCase(Locale.ROOT));
  }

  @Override public Key animatedTextureFont(UiTexture texture, ScreenAnchor anchor, int y) {
    if (!texture.equals(UiTexture.card(20))) throw new IllegalArgumentException("Animated texture requires the toast card");
    return font("toast_card", anchor, y);
  }

  private static Key font(String kind, ScreenAnchor anchor, int y) {
    if (y < -256 || y > 256) {
      throw new IllegalArgumentException("Anchored glyph origin must be within -256..256 GUI pixels");
    }
    return Key.key("vexcore:ui/v26_2/" + kind + "/" + anchor.name().toLowerCase(Locale.ROOT) + "/y_0");
  }

  private static Properties loadProtocol() {
    try (var input = V26_2ScreenUiVersionDefinition.class.getResourceAsStream("protocol.properties")) {
      if (input == null) {
        throw new IllegalStateException("Missing screen UI protocol manifest");
      }
      Properties result = new Properties();
      result.load(input);
      return result;
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot load screen UI protocol", exception);
    }
  }
}
