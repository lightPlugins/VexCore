package dev.vexsoft.core.currency;

import dev.vexsoft.core.api.player.PlayerContainer;
import dev.vexsoft.core.number.WholeAmount;
import java.util.Map;

/** Persistent virtual-currency balances attached to one loaded Vex player. */
public interface CurrencyContainer extends PlayerContainer {

    /** Returns the current persistent balance or the definition's default balance. */
    WholeAmount getBalance(Currency currency);

    /** Atomically adds a positive amount to one currency. */
    CurrencyTransaction deposit(Currency currency, WholeAmount amount);

    /** Atomically deposits every positive amount or applies none of them. */
    CurrencyBatchTransaction depositAll(Map<Currency, WholeAmount> amounts);

    /** Atomically removes a positive amount when sufficient balance is available. */
    CurrencyTransaction withdraw(Currency currency, WholeAmount amount);

    /** Atomically replaces one balance with a non-negative value inside its configured maximum. */
    CurrencyTransaction setBalance(Currency currency, WholeAmount balance);
}
