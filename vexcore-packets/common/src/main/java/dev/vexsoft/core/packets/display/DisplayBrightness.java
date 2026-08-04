package dev.vexsoft.core.packets.display;

import lombok.Value;

@Value
public class DisplayBrightness {
  int block;
  int sky;

  public DisplayBrightness(final int block, final int sky) {
    if (block < 0 || block > 15 || sky < 0 || sky > 15) {
      throw new IllegalArgumentException("brightness values must be between 0 and 15");
    }
    this.block = block;
    this.sky = sky;
  }
}
