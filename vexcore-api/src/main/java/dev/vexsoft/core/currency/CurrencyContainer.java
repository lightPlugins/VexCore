package dev.vexsoft.core.currency;

import dev.vexsoft.core.api.player.PlayerContainer;
import java.util.Map;

/** Persistent virtual-currency balances attached to one loaded Vex player. */
public interface CurrencyContainer extends PlayerContainer {

  /** Returns the current persistent balance or the definition's default balance. */
  long getBalance(Currency currency);

  /** Atomically adds a positive amount to one currency. */
  CurrencyTransaction deposit(Currency currency, long amount);

  /** Atomically deposits every positive amount or applies none of them. */
  CurrencyBatchTransaction depositAll(Map<Currency, Long> amounts);

  /** Atomically removes a positive amount when sufficient balance is available. */
  CurrencyTransaction withdraw(Currency currency, long amount);

  /** Atomically replaces one balance with a non-negative value inside its configured maximum. */
  CurrencyTransaction setBalance(Currency currency, long balance);
}
