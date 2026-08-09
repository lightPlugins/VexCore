package dev.vexsoft.core.common.service.reactor;

import dev.vexsoft.core.reactor.expression.CompiledExpression;

import dev.vexsoft.core.api.service.reactor.ExpressionService;
import dev.vexsoft.core.api.service.placeholder.PlaceholderService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.reactor.context.ReactorContext;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import dev.vexsoft.core.common.service.placeholder.PlaceholderRegistryCoordinatorService;
import dev.vexsoft.core.common.service.placeholder.VexPlaceholderRegistryCoordinatorService;
import dev.vexsoft.core.common.service.placeholder.VexPlaceholderService;
import org.junit.jupiter.api.Test;

class VexExpressionServiceTest {

  private final ExpressionService expressions = createExpressionService();

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

  private static ExpressionService createExpressionService() {
    VexServiceRegistry services = new DefaultServiceRegistry().scoped(
        (ServiceOwner) () -> "test"
    );
    services.register(
        PlaceholderRegistryCoordinatorService.class,
        VexPlaceholderRegistryCoordinatorService.class
    );
    services.register(PlaceholderService.class, VexPlaceholderService.class);
    services.registerQueuedServices();
    return new VexExpressionService(services);
  }
}
