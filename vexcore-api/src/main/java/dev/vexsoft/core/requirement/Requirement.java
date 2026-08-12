package dev.vexsoft.core.requirement;

/** Compiles the raw value belonging to one registered requirement configuration key. */
public interface Requirement {

  /** Validates and compiles one configured requirement value. */
  CompiledRequirement compile(Object value);
}
