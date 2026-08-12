package dev.vexsoft.core.expression;

/** Evaluates one validated expression against a runtime context. */
public interface CompiledExpression {

  /** Evaluates this expression as a numeric value. */
  double evaluateNumber(EvaluationContext context);

  /** Evaluates this expression as a boolean value. */
  boolean evaluateBoolean(EvaluationContext context);

  /** Evaluates this expression as text. */
  String evaluateString(EvaluationContext context);
}
