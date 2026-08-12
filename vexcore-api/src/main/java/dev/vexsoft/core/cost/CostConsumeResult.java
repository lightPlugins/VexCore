package dev.vexsoft.core.cost;

import java.util.Optional;

/** Result and optional receipt from consuming or refunding a cost. */
public record CostConsumeResult(boolean successful, String message, CostReceipt receipt) {

  /** Creates a successful result with a compensation receipt. */
  public static CostConsumeResult success(final CostReceipt receipt) {
    return new CostConsumeResult(true, "", receipt);
  }

  /** Creates a successful result without a receipt. */
  public static CostConsumeResult success() {
    return new CostConsumeResult(true, "", null);
  }

  /** Creates a failed result. */
  public static CostConsumeResult failed(final String message) {
    return new CostConsumeResult(false, message, null);
  }

  /** Returns the compensation receipt when resources were consumed. */
  public Optional<CostReceipt> getReceipt() {
    return Optional.ofNullable(receipt);
  }
}
