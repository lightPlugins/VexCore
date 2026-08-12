package dev.vexsoft.core.paper.cost.coin;

import dev.vexsoft.core.api.service.expression.ExpressionService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.cost.CompiledCost;
import dev.vexsoft.core.cost.Cost;
import dev.vexsoft.core.cost.CostCheckResult;
import dev.vexsoft.core.cost.CostConsumeResult;
import dev.vexsoft.core.cost.CostReceipt;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.execution.ExecutionDescription;
import dev.vexsoft.core.expression.CompiledExpression;
import dev.vexsoft.core.paper.service.economy.EconomyService;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Vault-backed {@code coins} cost with compensating refunds. */
@Dependencies({ExpressionService.class, EconomyService.class})
public final class VaultCoinCost implements Cost {

  private final ExpressionService expressions;
  private final EconomyService economy;

  /** Resolves expression and economy services. */
  public VaultCoinCost(final VexServiceRegistry services) {
    expressions = services.require(ExpressionService.class);
    economy = services.require(EconomyService.class);
  }

  @Override
  public CompiledCost compile(final Object value) {
    return new Compiled(expressions.compile(Objects.toString(value)), economy);
  }

  private record CoinReceipt(double amount) implements CostReceipt {}

  private record Compiled(CompiledExpression amount, EconomyService economy) implements CompiledCost {

    @Override
    public CostCheckResult check(final PlayerExecutionContext context) {
      double required = evaluate(context);
      return economy.getBalance(context.player()) >= required
          ? CostCheckResult.success()
          : CostCheckResult.unavailable("Insufficient coins");
    }

    @Override
    public CostConsumeResult consume(final PlayerExecutionContext context) {
      double required = evaluate(context);
      EconomyService.EconomyTransaction result = economy.withdraw(context.player(), required);
      return result.successful()
          ? CostConsumeResult.success(new CoinReceipt(result.amount()))
          : CostConsumeResult.failed(result.message());
    }

    @Override
    public CostConsumeResult refund(
        final PlayerExecutionContext context,
        final CostReceipt receipt
    ) {
      if (!(receipt instanceof CoinReceipt coins)) {
        return CostConsumeResult.failed("Invalid coin receipt");
      }
      EconomyService.EconomyTransaction result = economy.deposit(context.player(), coins.amount());
      return result.successful() ? CostConsumeResult.success() : CostConsumeResult.failed(result.message());
    }

    @Override
    public Component describe(final PlayerExecutionContext context) {
      return Component.text(economy.format(evaluate(context)), NamedTextColor.RED);
    }

    @Override
    public List<ExecutionDescription> describeEntries(final PlayerExecutionContext context) {
      return List.of(new ExecutionDescription(
          "",
          Map.of("amount", Component.text(economy.format(evaluate(context)))),
          describe(context)
      ));
    }

    private double evaluate(final PlayerExecutionContext context) {
      double value = amount.evaluateNumber(context);
      if (!Double.isFinite(value) || value <= 0D) {
        throw new IllegalStateException("Coin cost must evaluate to a positive finite amount");
      }
      return value;
    }
  }
}
