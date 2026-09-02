package dev.vexsoft.core.currency;

import java.util.Map;
import java.util.Objects;
import dev.vexsoft.core.number.WholeAmount;

/** Result of an atomic multi-currency deposit. */
public record CurrencyBatchTransaction(
    boolean successful,
    String message,
    Map<CurrencyKey, WholeAmount> balances
) {

  /** Copies the resulting balance snapshot and normalizes the diagnostic message. */
  public CurrencyBatchTransaction {
    message = message == null ? "" : message;
    balances = Map.copyOf(Objects.requireNonNull(balances, "balances"));
  }
}
