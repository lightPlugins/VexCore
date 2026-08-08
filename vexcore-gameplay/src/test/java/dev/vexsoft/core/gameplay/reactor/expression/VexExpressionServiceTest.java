package dev.vexsoft.core.gameplay.reactor.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.gameplay.reactor.context.ReactorContext;
import dev.vexsoft.core.service.DefaultServiceRegistry;
import org.junit.jupiter.api.Test;

class VexExpressionServiceTest {

  private final ExpressionService expressions = new VexExpressionService(
      new DefaultServiceRegistry().scoped((ServiceOwner) () -> "test")
  );

  @Test
  void evaluatesHyphenatedContextPlaceholdersFromCachedSyntaxTree() {
    CompiledExpression expression = expressions.compile("1000 * %victim-level%");
    ReactorContext context = new ReactorContext() { };

    assertEquals(4_000D, expression.evaluateNumber(variable("victim-level", 4D)));
    assertEquals(7_000D, expression.evaluateNumber(variable("victim-level", 7D)));
    assertThrows(IllegalStateException.class, () -> expression.evaluateNumber(context));
  }

  @Test
  void evaluatesConstantsWithoutRuntimeVariables() {
    CompiledExpression expression = expressions.compile("1.25");

    assertEquals(1.25D, expression.evaluateNumber(variable("unused", 1D)));
  }

  private static ReactorContext variable(final String expectedName, final Object value) {
    return new ReactorContext() {
      @Override
      public Object getVariable(final String name) {
        return expectedName.equals(name) ? value : null;
      }
    };
  }
}
