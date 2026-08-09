package dev.vexsoft.core.paper.reactor.effect;

import dev.vexsoft.core.paper.reactor.context.MutableDamageReactorContext;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.reactor.effect.CompiledEffect;
import dev.vexsoft.core.reactor.effect.Effect;
import dev.vexsoft.core.reactor.ReactorId;
import dev.vexsoft.core.reactor.expression.CompiledExpression;
import dev.vexsoft.core.api.service.reactor.ExpressionService;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@ReactorId("modify-damage")
@Dependencies(ExpressionService.class)
public final class ModifyDamageEffect implements Effect<MutableDamageReactorContext> {

  private final ExpressionService expressions;

  public ModifyDamageEffect(final VexServiceRegistry services) {
    expressions = Objects.requireNonNull(services, "services").require(ExpressionService.class);
  }

  @Override
  public Class<MutableDamageReactorContext> getContextType() {
    return MutableDamageReactorContext.class;
  }

  @Override
  public CompiledEffect<MutableDamageReactorContext> compile(final Map<String, Object> arguments) {
    Operation operation = Operation.parse(arguments.getOrDefault("operation", "set"));
    Object configured = arguments.get("value");
    if (!(configured instanceof Number) && !(configured instanceof String)) {
      throw new IllegalArgumentException("Modify-damage effect requires a numeric value expression");
    }
    CompiledExpression value = expressions.compile(configured.toString());
    return context -> context.setDamage(
        operation.apply(context.getDamage(), value.evaluateNumber(context))
    );
  }

  private enum Operation {
    SET {
      @Override double apply(final double damage, final double value) {
        return value;
      }
    },
    ADD {
      @Override double apply(final double damage, final double value) {
        return damage + value;
      }
    },
    MULTIPLY {
      @Override double apply(final double damage, final double value) {
        return damage * value;
      }
    };

    abstract double apply(double damage, double value);

    private static Operation parse(final Object value) {
      if (!(value instanceof String text)) {
        throw new IllegalArgumentException("Damage operation must be a string");
      }
      try {
        return valueOf(text.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
      } catch (IllegalArgumentException exception) {
        throw new IllegalArgumentException("Unknown damage operation: " + text, exception);
      }
    }
  }
}
