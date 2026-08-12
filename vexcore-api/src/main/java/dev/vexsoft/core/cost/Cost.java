package dev.vexsoft.core.cost;

/** Compiles the raw value belonging to one registered cost configuration key. */
public interface Cost {

  /** Validates and compiles one configured cost value. */
  CompiledCost compile(Object value);
}
