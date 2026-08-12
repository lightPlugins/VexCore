package dev.vexsoft.core.api.service.expression;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.expression.CompiledExpression;

/** Compiles reusable expressions containing percent-delimited context variables. */
public interface ExpressionService extends VexService {

  /** Compiles and validates an expression. */
  CompiledExpression compile(String expression);
}
