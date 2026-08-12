package dev.vexsoft.core.cost;

/** Non-mutating cost availability result. */
public record CostCheckResult(boolean affordable, String message) {

  /** Creates an affordable result. */
  public static CostCheckResult success() {
    return new CostCheckResult(true, "");
  }

  /** Creates an unavailable result. */
  public static CostCheckResult unavailable(final String message) {
    return new CostCheckResult(false, message);
  }
}
