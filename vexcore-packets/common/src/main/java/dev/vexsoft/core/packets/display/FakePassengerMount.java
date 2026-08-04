package dev.vexsoft.core.packets.display;

import java.util.Objects;
import lombok.Value;

@Value
public class FakePassengerMount {
  FakeDisplayHandle handle;
  float offsetX;
  float offsetY;
  float offsetZ;

  public FakePassengerMount(
      final FakeDisplayHandle handle,
      final float offsetX,
      final float offsetY,
      final float offsetZ
  ) {
    this.handle = Objects.requireNonNull(handle, "handle");
    this.offsetX = offsetX;
    this.offsetY = offsetY;
    this.offsetZ = offsetZ;
  }

  public static FakePassengerMount of(final FakeDisplayHandle handle) {
    return new FakePassengerMount(handle, 0.0F, 0.0F, 0.0F);
  }
}
