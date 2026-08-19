package dev.vexsoft.core.currency;

import java.util.Map;
import java.util.Objects;

/** Result of an atomic multi-currency deposit. */
public record CurrencyBatchTransaction(
    boolean successful,
    String message,
    Map<CurrencyKey, Long> balances
) {

  /** Copies the resulting balance snapshot and normalizes the diagnostic message. */
  public CurrencyBatchTransaction {
    message = message == null ? "" : message;
    balances = Map.copyOf(Objects.requireNonNull(balances, "balances"));
  }
}
