package dev.vexsoft.core.gameplay.stat;

/** Mutable player-specific view of one active stat. */
public interface PlayerStat {

  /** Returns the runtime stat represented by this view. */
  Stat getStat();

  /** Returns the cached final value. */
  double getValue();

  /** Returns the persisted permanent contribution. */
  double getPermanent();

  /** Replaces the persisted permanent contribution. */
  void setPermanent(double value);

  /** Adds to the persisted permanent contribution. */
  void addPermanent(double amount);

  /** Applies a removable, non-persistent runtime modifier. */
  StatModifierHandle addModifier(StatModifier modifier);
}
