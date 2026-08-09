package dev.vexsoft.core.paper.packets.display;

import lombok.Value;

/** Immutable block- and sky-light override using Minecraft's {@code 0..15} light levels. */
@Value
public class DisplayBrightness {
  int block;
  int sky;

  /** Creates a brightness override after validating both light levels. */
  public DisplayBrightness(final int block, final int sky) {
    if (block < 0 || block > 15 || sky < 0 || sky > 15) {
      throw new IllegalArgumentException("brightness values must be between 0 and 15");
    }
    this.block = block;
    this.sky = sky;
  }
}
