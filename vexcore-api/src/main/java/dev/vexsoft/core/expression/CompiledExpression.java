package dev.vexsoft.core.expression;

import java.math.BigDecimal;

/** Evaluates one validated expression against a runtime context. */
public interface CompiledExpression {

    /** Evaluates this expression as an exact decimal value. */
    BigDecimal evaluateDecimal(EvaluationContext context);

    /** Evaluates this expression as a numeric value. */
    default double evaluateNumber(final EvaluationContext context) {
        return evaluateDecimal(context).doubleValue();
    }

    /** Evaluates this expression as a boolean value. */
    boolean evaluateBoolean(EvaluationContext context);

    /** Evaluates this expression as text. */
    String evaluateString(EvaluationContext context);
}
