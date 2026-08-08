package dev.vexsoft.core.gameplay.reactor.expression;

import dev.vexsoft.core.api.service.VexService;

/** Compiles reusable expressions and their percent-delimited context placeholders. */
public interface ExpressionService extends VexService {

  /** Parses and validates an expression for repeated runtime evaluation. */
  CompiledExpression compile(String expression);
}
