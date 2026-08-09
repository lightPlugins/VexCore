package dev.vexsoft.core.paper.packets.display;

import java.util.Objects;
import lombok.Value;

/** Associates a fake display passenger with an offset from its real vehicle. */
@Value
public class FakePassengerMount {
  FakeDisplayHandle handle;
  float offsetX;
  float offsetY;
  float offsetZ;

  /** Creates a mount with an explicit relative offset. */
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

  /** Creates a mount without a relative offset. */
  public static FakePassengerMount of(final FakeDisplayHandle handle) {
    return new FakePassengerMount(handle, 0.0F, 0.0F, 0.0F);
  }
}
