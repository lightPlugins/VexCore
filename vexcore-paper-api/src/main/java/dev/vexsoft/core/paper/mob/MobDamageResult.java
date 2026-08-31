package dev.vexsoft.core.paper.mob;

/** Result of applying custom damage to one runtime mob. */
public record MobDamageResult(boolean applied, boolean killed, double previousHealth,
                              double currentHealth) {

  /** Creates a result for a rejected damage request. */
  public static MobDamageResult rejected(final double health) {
    return new MobDamageResult(false, false, health, health);
  }
}
