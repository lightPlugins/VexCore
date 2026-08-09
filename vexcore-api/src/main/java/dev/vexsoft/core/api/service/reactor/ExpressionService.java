package dev.vexsoft.core.api.service.reactor;

import dev.vexsoft.core.reactor.expression.CompiledExpression;

import dev.vexsoft.core.api.service.registry.VexService;

/** Compiles reusable expressions and their percent-delimited context placeholders. */
public interface ExpressionService extends VexService {

  /** Parses and validates an expression for repeated runtime evaluation. */
  CompiledExpression compile(String expression);
}
