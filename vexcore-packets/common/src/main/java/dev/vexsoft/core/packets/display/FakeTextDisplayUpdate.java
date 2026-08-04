package dev.vexsoft.core.packets.display;

import lombok.Builder;
import lombok.Value;
import net.kyori.adventure.text.Component;

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

  public static FakeTextDisplayUpdate text(final Component text) {
    return builder().text(text).build();
  }
}
