package dev.vexsoft.core.common.service.currency;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyBatchTransaction;
import dev.vexsoft.core.currency.CurrencyContainer;
import dev.vexsoft.core.currency.CurrencyKey;
import dev.vexsoft.core.currency.CurrencyTransaction;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** VexPlayer-backed persistent implementation of virtual-currency balances. */
public final class VexCurrencyContainer implements CurrencyContainer {

  private final VexPlayer player;

  /** Binds currency access to one loaded Vex player. */
  public VexCurrencyContainer(final VexPlayer player) {
    this.player = Objects.requireNonNull(player, "player");
  }

  @Override
  public long getBalance(final Currency currency) {
    Currency checked = requireAvailable(currency);
    return player.read(CurrencyPlayerData.CURRENCIES, data -> balance(data, checked));
  }

  @Override
  public CurrencyTransaction deposit(final Currency currency, final long amount) {
    Currency checked = available(currency);
    if (checked == null) {
      return failed(CurrencyTransaction.Status.UNAVAILABLE, "currency-unavailable");
    }
    if (amount <= 0L) {
      return failed(CurrencyTransaction.Status.INVALID_AMOUNT, "invalid-amount");
    }
    return player.update(CurrencyPlayerData.CURRENCIES, data -> {
      long previous = balance(data, checked);
      long updated;
      try {
        updated = Math.addExact(previous, amount);
      } catch (ArithmeticException exception) {
        return transaction(
            CurrencyTransaction.Status.MAXIMUM_EXCEEDED,
            previous,
            previous,
            "maximum-exceeded"
        );
      }
      if (updated > checked.getDefinition().getMaximumBalance()) {
        return transaction(
            CurrencyTransaction.Status.MAXIMUM_EXCEEDED,
            previous,
            previous,
            "maximum-exceeded"
        );
      }
      data.getBalances().put(checked.getKey().toString(), updated);
      return transaction(CurrencyTransaction.Status.SUCCESS, previous, updated, "");
    });
  }

  @Override
  public CurrencyBatchTransaction depositAll(final Map<Currency, Long> amounts) {
    Map<Currency, Long> checkedAmounts = new LinkedHashMap<>();
    Objects.requireNonNull(amounts, "amounts").forEach((currency, amount) -> {
      Currency checked = requireAvailable(currency);
      long checkedAmount = Objects.requireNonNull(amount, "amount");
      if (checkedAmount <= 0L) {
        throw new IllegalArgumentException("Currency deposit amount must be positive");
      }
      checkedAmounts.merge(checked, checkedAmount, Math::addExact);
    });
    return player.update(CurrencyPlayerData.CURRENCIES, data -> {
      Map<CurrencyKey, Long> updated = new LinkedHashMap<>();
      for (Map.Entry<Currency, Long> entry : checkedAmounts.entrySet()) {
        Currency currency = entry.getKey();
        long previous = balance(data, currency);
        long balance;
        try {
          balance = Math.addExact(previous, entry.getValue());
        } catch (ArithmeticException exception) {
          return new CurrencyBatchTransaction(false, "maximum-exceeded", Map.of());
        }
        if (balance > currency.getDefinition().getMaximumBalance()) {
          return new CurrencyBatchTransaction(false, "maximum-exceeded", Map.of());
        }
        updated.put(currency.getKey(), balance);
      }
      updated.forEach((key, balance) -> data.getBalances().put(key.toString(), balance));
      return new CurrencyBatchTransaction(true, "", updated);
    });
  }

  @Override
  public CurrencyTransaction withdraw(final Currency currency, final long amount) {
    Currency checked = available(currency);
    if (checked == null) {
      return failed(CurrencyTransaction.Status.UNAVAILABLE, "currency-unavailable");
    }
    if (amount <= 0L) {
      return failed(CurrencyTransaction.Status.INVALID_AMOUNT, "invalid-amount");
    }
    return player.update(CurrencyPlayerData.CURRENCIES, data -> {
      long previous = balance(data, checked);
      if (previous < amount) {
        return transaction(
            CurrencyTransaction.Status.INSUFFICIENT_BALANCE,
            previous,
            previous,
            "insufficient-balance"
        );
      }
      long updated = previous - amount;
      data.getBalances().put(checked.getKey().toString(), updated);
      return transaction(CurrencyTransaction.Status.SUCCESS, previous, updated, "");
    });
  }

  @Override
  public CurrencyTransaction setBalance(final Currency currency, final long balance) {
    Currency checked = available(currency);
    if (checked == null) {
      return failed(CurrencyTransaction.Status.UNAVAILABLE, "currency-unavailable");
    }
    if (balance < 0L) {
      return failed(CurrencyTransaction.Status.INVALID_AMOUNT, "invalid-amount");
    }
    if (balance > checked.getDefinition().getMaximumBalance()) {
      return failed(CurrencyTransaction.Status.MAXIMUM_EXCEEDED, "maximum-exceeded");
    }
    return player.update(CurrencyPlayerData.CURRENCIES, data -> {
      long previous = balance(data, checked);
      data.getBalances().put(checked.getKey().toString(), balance);
      return transaction(CurrencyTransaction.Status.SUCCESS, previous, balance, "");
    });
  }

  @Override
  public void onDataReset(final DataContainerKey<?> key) {
    // Balances are read directly from VexPlayer data, so no derived cache needs rebuilding.
  }

  private static long balance(final CurrencyData data, final Currency currency) {
    long value = data.getBalances().getOrDefault(
        currency.getKey().toString(),
        currency.getDefinition().getDefaultBalance()
    );
    if (value < 0L) {
      throw new IllegalStateException("Persisted currency balance must not be negative");
    }
    return value;
  }

  private static Currency requireAvailable(final Currency currency) {
    Currency checked = available(currency);
    if (checked == null) {
      throw new IllegalStateException("Currency registration is unavailable");
    }
    return checked;
  }

  private static Currency available(final Currency currency) {
    Currency checked = Objects.requireNonNull(currency, "currency");
    return checked.isRegistered() ? checked : null;
  }

  private static CurrencyTransaction failed(
      final CurrencyTransaction.Status status,
      final String message
  ) {
    return transaction(status, 0L, 0L, message);
  }

  private static CurrencyTransaction transaction(
      final CurrencyTransaction.Status status,
      final long previous,
      final long balance,
      final String message
  ) {
    return new CurrencyTransaction(status, previous, balance, message);
  }
}
