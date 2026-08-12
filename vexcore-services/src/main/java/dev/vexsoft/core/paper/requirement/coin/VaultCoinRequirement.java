package dev.vexsoft.core.paper.requirement.coin;

import dev.vexsoft.core.api.service.expression.ExpressionService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.execution.ExecutionDescription;
import dev.vexsoft.core.expression.CompiledExpression;
import dev.vexsoft.core.paper.service.economy.EconomyService;
import dev.vexsoft.core.requirement.CompiledRequirement;
import dev.vexsoft.core.requirement.Requirement;
import dev.vexsoft.core.requirement.RequirementResult;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Vault-backed minimum-balance {@code coins} requirement. */
@Dependencies({ExpressionService.class, EconomyService.class})
public final class VaultCoinRequirement implements Requirement {

  private final ExpressionService expressions;
  private final EconomyService economy;

  /** Resolves expression and economy services. */
  public VaultCoinRequirement(final VexServiceRegistry services) {
    expressions = services.require(ExpressionService.class);
    economy = services.require(EconomyService.class);
  }

  @Override
  public CompiledRequirement compile(final Object value) {
    return new Compiled(expressions.compile(Objects.toString(value)), economy);
  }

  private record Compiled(CompiledExpression amount, EconomyService economy)
      implements CompiledRequirement {

    @Override
    public RequirementResult test(final PlayerExecutionContext context) {
      double required = evaluate(context);
      double current = economy.getBalance(context.player());
      return current >= required
          ? RequirementResult.success()
          : RequirementResult.missing("Requires " + economy.format(required));
    }

    @Override
    public Component describe(final PlayerExecutionContext context) {
      double required = evaluate(context);
      double current = economy.getBalance(context.player());
      boolean satisfied = current >= required;
      return Component.text(satisfied ? "✔ " : "✘ ",
              satisfied ? NamedTextColor.GREEN : NamedTextColor.RED)
          .append(Component.text(economy.format(required), NamedTextColor.GOLD))
          .append(Component.text(" (" + economy.format(current) + ')', NamedTextColor.GRAY));
    }

    @Override
    public List<ExecutionDescription> describeEntries(final PlayerExecutionContext context) {
      double required = evaluate(context);
      boolean satisfied = economy.getBalance(context.player()) >= required;
      return List.of(new ExecutionDescription(
          satisfied ? "satisfied" : "missing",
          Map.of(
              "amount", Component.text(economy.format(required)),
              "state_symbol", symbol(satisfied)
          ),
          describe(context)
      ));
    }

    private static Component symbol(final boolean satisfied) {
      return Component.text(
          satisfied ? "\u2714" : "\u2718",
          satisfied ? NamedTextColor.GREEN : NamedTextColor.RED
      );
    }

    private double evaluate(final PlayerExecutionContext context) {
      double value = amount.evaluateNumber(context);
      if (!Double.isFinite(value) || value < 0D) {
        throw new IllegalStateException("Coin requirement must evaluate to a non-negative amount");
      }
      return value;
    }
  }
}
