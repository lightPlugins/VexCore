package dev.vexsoft.core.paper.screenui;

/**
 * Client-frame toast animation: 5 ticks in, 60 ticks held, 8 ticks out. Use World.getGameTime(),
 * never day time or wall time. Remove the element after TOTAL_TICKS. The versioned transport
 * quantizes animated colors to three bits per channel.
 */
public record UiAnimation(int startTick) {
  public static final int TOTAL_TICKS = 73;

  /** Validates that the start tick fits within the client animation clock. */
  public UiAnimation {
    if (startTick < 0 || startTick >= 24000) {
      throw new IllegalArgumentException("Invalid animation clock");
    }
  }

  /** Creates an animation using the wrapped world clock. */
  public static UiAnimation startingAt(long gameTime) {
    return new UiAnimation((int) Math.floorMod(gameTime, 24000));
  }
}
