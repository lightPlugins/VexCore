package dev.vexsoft.core.expression;

/** Supplies named runtime variables to compiled expressions. */
public interface EvaluationContext {

  /** Resolves a variable name without percent delimiters. */
  default Object getVariable(final String name) {
    return null;
  }
}
