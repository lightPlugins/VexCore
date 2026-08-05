package dev.vexsoft.core.packets.display;

import lombok.Builder;
import lombok.Value;
import net.kyori.adventure.text.Component;

/** Partial text-display update in which {@code null} properties remain unchanged. */
@Value
@Builder
public class FakeTextDisplayUpdate {
  Component text;
  Integer lineWidth;
  Integer backgroundColor;
  Byte textOpacity;
  Boolean shadowed;
  Boolean seeThrough;
  Boolean defaultBackground;
  TextDisplayAlignment alignment;
  DisplayTransformation transformation;
  DisplayBillboard billboard;
  DisplayBrightness brightness;
  Float viewRange;
  Float shadowRadius;
  Float shadowStrength;
  Float displayWidth;
  Float displayHeight;
  Integer interpolationDelay;
  Integer interpolationDuration;
  Integer teleportDuration;

  /** Creates an update that only replaces the displayed text. */
  public static FakeTextDisplayUpdate text(final Component text) {
    return builder().text(text).build();
  }
}
