package dev.vexsoft.core.gameplay.reactor.expression;

import dev.vexsoft.core.gameplay.reactor.context.ReactorContext;

/** Evaluates one validated expression against a runtime reaction context. */
public interface CompiledExpression {

  /** Evaluates this expression as a numeric value. */
  double evaluateNumber(ReactorContext context);

  /** Evaluates this expression as a boolean value. */
  boolean evaluateBoolean(ReactorContext context);

  /** Evaluates this expression as text. */
  String evaluateString(ReactorContext context);
}
