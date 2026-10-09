package dev.vexsoft.core.expression;

import java.math.BigDecimal;
import java.util.Objects;

/** Immutable expression value captured before a reward transaction or a retry. */
public record ConstantExpression(BigDecimal value) implements CompiledExpression {

    /** Requires one already evaluated finite decimal. */
    public ConstantExpression {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public BigDecimal evaluateDecimal(final EvaluationContext context) {
        return value;
    }

    @Override
    public boolean evaluateBoolean(final EvaluationContext context) {
        return value.signum() != 0;
    }

    @Override
    public String evaluateString(final EvaluationContext context) {
        return value.toPlainString();
    }
}
