package dev.vexsoft.core.paper.packets.display;

import lombok.Builder;
import lombok.Value;
import org.bukkit.block.data.BlockData;

/** Partial block-display update in which {@code null} properties remain unchanged. */
@Value
@Builder
public class FakeBlockDisplayUpdate {
  BlockData blockData;
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
  Boolean glowing;
  DisplayGlowColor glowColor;

  /**
   * Returns a defensive copy of the replacement block data when supplied.
   *
   * @return copied replacement block state, or {@code null} when unchanged
   */
  public BlockData getBlockData() {
    return blockData == null ? null : blockData.clone();
  }

  /**
   * Creates an update that only replaces the displayed block data.
   *
   * @param blockData replacement block state
   * @return partial block-display update
   */
  public static FakeBlockDisplayUpdate block(final BlockData blockData) {
    return builder().blockData(blockData).build();
  }
}
