package dev.vexsoft.core.common.cost.currency;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.currency.CurrencyLocalizationService;
import dev.vexsoft.core.api.service.currency.CurrencyRegistry;
import dev.vexsoft.core.api.service.expression.ExpressionService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.cost.CompiledCost;
import dev.vexsoft.core.cost.Cost;
import dev.vexsoft.core.cost.CostCheckResult;
import dev.vexsoft.core.cost.CostConsumeResult;
import dev.vexsoft.core.cost.CostReceipt;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyContainer;
import dev.vexsoft.core.currency.CurrencyKey;
import dev.vexsoft.core.currency.CurrencyTransaction;
import dev.vexsoft.core.execution.ExecutionDescription;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.expression.CompiledExpression;
import dev.vexsoft.core.number.WholeAmount;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Persistent {@code currencies} cost with exact compensating receipts. */
@Dependencies({
    ExpressionService.class,
    CurrencyRegistry.class,
    CurrencyLocalizationService.class
})
public final class CurrencyCost implements Cost {

  private final ExpressionService expressions;
  private final CurrencyRegistry currencies;
  private final CurrencyLocalizationService localizations;

  /** Resolves the shared expression and currency services. */
  public CurrencyCost(final VexServiceRegistry services) {
    VexServiceRegistry checked = Objects.requireNonNull(services, "services");
    expressions = checked.require(ExpressionService.class);
    currencies = checked.require(CurrencyRegistry.class);
    localizations = checked.require(CurrencyLocalizationService.class);
  }

  @Override
  public CompiledCost compile(final Object value) {
    Map<String, Object> configured = values(value);
    if (configured.isEmpty()) {
      throw new IllegalArgumentException("currencies cost must contain at least one currency");
    }
    Map<Currency, CompiledExpression> compiled = new LinkedHashMap<>();
    configured.forEach((key, amount) -> compiled.put(
        currencies.require(CurrencyKey.parse(key)),
        expressions.compile(Objects.toString(amount))
    ));
    return new Compiled(Map.copyOf(compiled), localizations);
  }

  private static Map<String, Object> values(final Object value) {
    if (value instanceof ConfigurationSection section) {
      return section.getValues(false);
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, entry) -> result.put(Objects.toString(key), entry));
      return result;
    }
    throw new IllegalArgumentException("currencies cost must be a map");
  }

  private record CurrencyReceipt(Map<Currency, WholeAmount> amounts) implements CostReceipt {
    private CurrencyReceipt {
      amounts = Map.copyOf(amounts);
    }
  }

  private record Compiled(
      Map<Currency, CompiledExpression> amounts,
      CurrencyLocalizationService localizations
  ) implements CompiledCost {

    @Override
    public CostCheckResult check(final PlayerExecutionContext context) {
      CurrencyContainer container = context.player().getContainer(CurrencyContainer.class);
      return check(container, evaluate(context));
    }

    private static CostCheckResult check(
        final CurrencyContainer container,
        final Map<Currency, WholeAmount> evaluated
    ) {
      for (Map.Entry<Currency, WholeAmount> entry : evaluated.entrySet()) {
        if (container.getBalance(entry.getKey()).compareTo(entry.getValue()) < 0) {
          return CostCheckResult.unavailable("Insufficient currency");
        }
      }
      return CostCheckResult.success();
    }

    @Override
    public CostConsumeResult consume(final PlayerExecutionContext context) {
      Map<Currency, WholeAmount> evaluated = evaluate(context);
      CurrencyContainer container = context.player().getContainer(CurrencyContainer.class);
      if (!check(container, evaluated).affordable()) {
        return CostConsumeResult.failed("Insufficient currency");
      }
      Map<Currency, WholeAmount> consumed = new LinkedHashMap<>();
      for (Map.Entry<Currency, WholeAmount> entry : evaluated.entrySet()) {
        CurrencyTransaction transaction = container.withdraw(entry.getKey(), entry.getValue());
        if (!transaction.successful()) {
          consumed.forEach(container::deposit);
          return CostConsumeResult.failed(transaction.message());
        }
        consumed.put(entry.getKey(), entry.getValue());
      }
      return CostConsumeResult.success(new CurrencyReceipt(consumed));
    }

    @Override
    public CostConsumeResult refund(
        final PlayerExecutionContext context,
        final CostReceipt receipt
    ) {
      if (!(receipt instanceof CurrencyReceipt currencyReceipt)) {
        return CostConsumeResult.failed("Invalid currency receipt");
      }
      CurrencyContainer container = context.player().getContainer(CurrencyContainer.class);
      Map<Currency, WholeAmount> refunded = new LinkedHashMap<>();
      for (Map.Entry<Currency, WholeAmount> entry : currencyReceipt.amounts().entrySet()) {
        CurrencyTransaction transaction = container.deposit(entry.getKey(), entry.getValue());
        if (!transaction.successful()) {
          refunded.forEach(container::withdraw);
          return CostConsumeResult.failed(transaction.message());
        }
        refunded.put(entry.getKey(), entry.getValue());
      }
      return CostConsumeResult.success();
    }

    @Override
    public Component describe(final PlayerExecutionContext context) {
      Component result = Component.empty();
      List<ExecutionDescription> entries = describeEntries(context);
      for (int index = 0; index < entries.size(); index++) {
        if (index > 0) {
          result = result.append(Component.text(", "));
        }
        result = result.append(entries.get(index).fallback());
      }
      return result;
    }

    @Override
    public List<ExecutionDescription> describeEntries(final PlayerExecutionContext context) {
      List<ExecutionDescription> result = new ArrayList<>();
      evaluate(context).forEach((currency, amount) -> result.add(new ExecutionDescription(
          "",
          Map.of(
              "amount", Component.text(amount.toString()),
              "formatted_amount", Component.text(localizations.formatCompact(amount)),
              "currency", localizations.getName(context.player(), currency.getKey()),
              "currency_key", Component.text(currency.getKey().toString())
          ),
          localizations.format(context.player(), currency.getKey(), amount)
      )));
      return List.copyOf(result);
    }

    private Map<Currency, WholeAmount> evaluate(final PlayerExecutionContext context) {
      Map<Currency, WholeAmount> result = new LinkedHashMap<>();
      amounts.forEach((currency, expression) -> {
        BigDecimal value = expression.evaluateDecimal(context);
        try {
          WholeAmount amount = WholeAmount.of(value.toBigIntegerExact());
          if (!amount.isPositive()) {
            throw new ArithmeticException("non-positive");
          }
          result.put(currency, amount);
        } catch (ArithmeticException exception) {
          throw new IllegalStateException(
              "Currency cost amount for " + currency.getKey()
                  + " must evaluate to a positive whole number",
              exception
          );
        }
      });
      return Map.copyOf(result);
    }
  }
}
