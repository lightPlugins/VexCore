package dev.vexsoft.core.paper.reward.coin;

import dev.vexsoft.core.api.service.expression.ExpressionService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.execution.ExecutionDescription;
import dev.vexsoft.core.expression.CompiledExpression;
import dev.vexsoft.core.paper.service.economy.EconomyService;
import dev.vexsoft.core.reward.CompiledReward;
import dev.vexsoft.core.reward.Reward;
import dev.vexsoft.core.reward.RewardBehavior;
import dev.vexsoft.core.reward.RewardResult;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Vault-backed {@code coins} action reward. */
@Dependencies({ExpressionService.class, EconomyService.class})
public final class VaultCoinReward implements Reward {

  private final ExpressionService expressions;
  private final EconomyService economy;

  /** Resolves expression and economy services. */
  public VaultCoinReward(final VexServiceRegistry services) {
    expressions = services.require(ExpressionService.class);
    economy = services.require(EconomyService.class);
  }

  @Override
  public CompiledReward compile(final Object value) {
    return new Compiled(expressions.compile(Objects.toString(value)), economy);
  }

  private record Compiled(CompiledExpression amount, EconomyService economy)
      implements CompiledReward {

    @Override
    public RewardBehavior getBehavior() {
      return RewardBehavior.ACTION;
    }

    @Override
    public RewardResult grant(final PlayerExecutionContext context) {
      double evaluated = evaluate(context);
      EconomyService.EconomyTransaction result = economy.deposit(context.player(), evaluated);
      return result.successful() ? RewardResult.success() : RewardResult.failed(result.message());
    }

    @Override
    public Component describe(final PlayerExecutionContext context) {
      return Component.text('+' + economy.format(evaluate(context)), NamedTextColor.GOLD);
    }

    @Override
    public List<ExecutionDescription> describeEntries(final PlayerExecutionContext context) {
      String formatted = economy.format(evaluate(context));
      Component fallback = describe(context);
      return List.of(new ExecutionDescription(
          "", Map.of("amount", Component.text(formatted)), fallback
      ));
    }

    private double evaluate(final PlayerExecutionContext context) {
      double value = amount.evaluateNumber(context);
      if (!Double.isFinite(value) || value <= 0D) {
        throw new IllegalStateException("Coin reward must evaluate to a positive finite amount");
      }
      return value;
    }
  }
}
