package dev.vexsoft.core.common.gameplay.reactor.internal;

import dev.vexsoft.core.gameplay.reactor.expression.CompiledExpression;
import dev.vexsoft.core.api.service.reactor.ExpressionService;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class ReactorArguments {

  public static String string(final Map<String, Object> arguments, final String name) {
    Object value = Objects.requireNonNull(arguments, "arguments").get(name);
    if (!(value instanceof String text) || text.isBlank()) {
      throw new IllegalArgumentException("Reaction argument '" + name + "' must be a non-empty string");
    }
    return text.trim();
  }

  public static double number(final Map<String, Object> arguments, final String name) {
    Object value = Objects.requireNonNull(arguments, "arguments").get(name);
    if (!(value instanceof Number number)) {
      throw new IllegalArgumentException("Reaction argument '" + name + "' must be numeric");
    }
    double result = number.doubleValue();
    if (!Double.isFinite(result)) {
      throw new IllegalArgumentException("Reaction argument '" + name + "' must be finite");
    }
    return result;
  }

  public static CompiledExpression expression(
      final ExpressionService expressions,
      final Map<String, Object> arguments,
      final String name
  ) {
    Object value = Objects.requireNonNull(arguments, "arguments").get(name);
    if (value instanceof Number number) {
      return expressions.compile(number.toString());
    }
    if (value instanceof String text) {
      return expressions.compile(text);
    }
    throw new IllegalArgumentException(
        "Reaction argument '" + name + "' must be a number or expression"
    );
  }
}
