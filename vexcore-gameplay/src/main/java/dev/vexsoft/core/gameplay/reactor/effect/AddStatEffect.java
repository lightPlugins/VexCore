package dev.vexsoft.core.gameplay.reactor.effect;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.context.PlayerReactorContext;
import dev.vexsoft.core.gameplay.reactor.ReactorId;
import dev.vexsoft.core.gameplay.reactor.expression.CompiledExpression;
import dev.vexsoft.core.gameplay.reactor.expression.ExpressionService;
import dev.vexsoft.core.gameplay.reactor.internal.ReactorArguments;
import dev.vexsoft.core.gameplay.stat.Stat;
import dev.vexsoft.core.gameplay.stat.StatContainer;
import dev.vexsoft.core.gameplay.stat.StatKey;
import dev.vexsoft.core.gameplay.stat.StatRegistry;
import java.util.Map;
import java.util.Objects;

/** Adds an expression result to one player's permanent stat contribution. */
@ReactorId("add-stat")
@Dependencies({StatRegistry.class, ExpressionService.class})
public final class AddStatEffect implements Effect<PlayerReactorContext> {

  private final StatRegistry stats;
  private final ExpressionService expressions;

  /** Creates an effect backed by the shared stat and expression services. */
  public AddStatEffect(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    stats = checkedServices.require(StatRegistry.class);
    expressions = checkedServices.require(ExpressionService.class);
  }

  @Override
  public Class<PlayerReactorContext> getContextType() {
    return PlayerReactorContext.class;
  }

  @Override
  public CompiledEffect<PlayerReactorContext> compile(final Map<String, Object> arguments) {
    Stat stat = stats.require(StatKey.parse(ReactorArguments.string(arguments, "stat")));
    CompiledExpression amount = ReactorArguments.expression(expressions, arguments, "amount");
    return context -> context.getPlayer()
        .getContainer(StatContainer.class)
        .getStat(stat)
        .addPermanent(amount.evaluateNumber(context));
  }
}
