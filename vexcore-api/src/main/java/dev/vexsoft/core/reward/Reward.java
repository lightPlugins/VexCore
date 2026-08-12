package dev.vexsoft.core.reward;

/** Compiles the raw value belonging to one registered reward configuration key. */
public interface Reward {

  /** Validates and compiles one configured reward value. */
  CompiledReward compile(Object value);
}
