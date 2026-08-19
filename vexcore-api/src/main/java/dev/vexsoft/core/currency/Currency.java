package dev.vexsoft.core.currency;

/** One active runtime registration of a virtual currency. */
public interface Currency {

  /** Returns the stable persistence and registry key. */
  CurrencyKey getKey();

  /** Returns the current immutable definition. */
  CurrencyDefinition getDefinition();

  /** Returns whether this exact runtime registration remains active. */
  boolean isRegistered();
}
