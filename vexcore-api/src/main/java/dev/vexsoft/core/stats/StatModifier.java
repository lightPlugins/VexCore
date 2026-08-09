package dev.vexsoft.core.stats;

import java.util.Objects;

/** Immutable runtime modification applied to one player stat. */
public record StatModifier(StatOperation operation, double amount) {

  /** Validates a modifier operation and finite amount. */
  public StatModifier {
    Objects.requireNonNull(operation, "operation");
    if (!Double.isFinite(amount)) {
      throw new IllegalArgumentException("Stat modifier amount must be finite");
    }
    if (operation == StatOperation.TOTAL_MULTIPLIER && amount <= 0D) {
      throw new IllegalArgumentException("Total multiplier must be greater than zero");
    }
  }

  /** Creates a flat additive modifier. */
  public static StatModifier flat(final double amount) {
    return new StatModifier(StatOperation.FLAT, amount);
  }

  /** Creates an additive percentage modifier. */
  public static StatModifier additiveMultiplier(final double amount) {
    return new StatModifier(StatOperation.ADDITIVE_MULTIPLIER, amount);
  }

  /** Creates a final multiplicative modifier. */
  public static StatModifier totalMultiplier(final double amount) {
    return new StatModifier(StatOperation.TOTAL_MULTIPLIER, amount);
  }
}
