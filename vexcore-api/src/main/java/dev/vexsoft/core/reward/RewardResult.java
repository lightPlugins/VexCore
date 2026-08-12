package dev.vexsoft.core.reward;

import java.util.Objects;

/** Result of executing one action reward. */
public record RewardResult(Status status, String message) {

  /** Possible action outcomes. */
  public enum Status { SUCCESS, FAILED, SKIPPED }

  /** Creates a successful result. */
  public static RewardResult success() {
    return new RewardResult(Status.SUCCESS, "");
  }

  /** Creates a failed result. */
  public static RewardResult failed(final String message) {
    return new RewardResult(Status.FAILED, Objects.requireNonNull(message, "message"));
  }

  /** Creates a skipped result. */
  public static RewardResult skipped(final String message) {
    return new RewardResult(Status.SKIPPED, Objects.requireNonNull(message, "message"));
  }
}
