package dev.vexsoft.core.currency;

import java.util.Objects;

/** Result of one atomic virtual-currency mutation. */
public record CurrencyTransaction(
    Status status,
    long previousBalance,
    long balance,
    String message
) {

  /** Normalizes the optional diagnostic message. */
  public CurrencyTransaction {
    status = Objects.requireNonNull(status, "status");
    message = message == null ? "" : message;
  }

  /** Returns whether the requested balance mutation was accepted. */
  public boolean successful() {
    return status == Status.SUCCESS;
  }

  /** Stable outcome category for one balance mutation. */
  public enum Status {
    SUCCESS,
    INVALID_AMOUNT,
    INSUFFICIENT_BALANCE,
    MAXIMUM_EXCEEDED,
    UNAVAILABLE
  }
}
