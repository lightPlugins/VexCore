package dev.vexsoft.core.common.reward.currency;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.currency.CurrencyLocalizationService;
import dev.vexsoft.core.api.service.currency.CurrencyRegistry;
import dev.vexsoft.core.api.service.expression.ExpressionService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyBatchTransaction;
import dev.vexsoft.core.currency.CurrencyContainer;
import dev.vexsoft.core.currency.CurrencyKey;
import dev.vexsoft.core.execution.ExecutionDescription;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.expression.CompiledExpression;
import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.reward.CompiledReward;
import dev.vexsoft.core.reward.QuantifiedReward;
import dev.vexsoft.core.reward.Reward;
import dev.vexsoft.core.reward.RewardBehavior;
import dev.vexsoft.core.reward.RewardResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Persistent {@code currencies} action reward backed by the player's currency container. */
@Dependencies({ExpressionService.class, CurrencyRegistry.class, CurrencyLocalizationService.class})
public final class CurrencyReward implements Reward {

    private final ExpressionService expressions;
    private final CurrencyRegistry currencies;
    private final CurrencyLocalizationService localizations;

    /** Resolves expression, currency, and localization services. */
    public CurrencyReward(final VexServiceRegistry services) {
        VexServiceRegistry checked = Objects.requireNonNull(services, "services");

        expressions = checked.require(ExpressionService.class);
        currencies = checked.require(CurrencyRegistry.class);
        localizations = checked.require(CurrencyLocalizationService.class);
    }

    @Override
    public CompiledReward compile(final Object value) {
        Map<String, Object> configured = values(value);

        if (configured.isEmpty()) {
            throw new IllegalArgumentException("currencies reward must contain at least one currency");
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

        throw new IllegalArgumentException("currencies reward must be a map");
    }

    private record Compiled(Map<Currency, CompiledExpression> amounts,
                            CurrencyLocalizationService localizations) implements QuantifiedReward {

        @Override
        public Map<String, WholeAmount> quantities(final PlayerExecutionContext context) {
            Map<String, WholeAmount> result = new LinkedHashMap<>();

            evaluate(context).forEach((currency, amount) -> result.put(currency.getKey().toString(), amount));

            return Map.copyOf(result);
        }

        @Override
        public RewardBehavior getBehavior() {
            return RewardBehavior.ACTION;
        }

        @Override
        public boolean supportsPlayerRollback() {
            return true;
        }

        @Override
        public RewardResult grant(final PlayerExecutionContext context) {
            CurrencyBatchTransaction result =
                context.player().getContainer(CurrencyContainer.class).depositAll(evaluate(context));

            return result.successful() ? RewardResult.success() : RewardResult.failed(result.message());
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
            Map<Currency, WholeAmount> evaluated = evaluate(context);
            List<ExecutionDescription> result = new ArrayList<>();

            evaluated.forEach((currency, amount) -> {
                Component name = localizations.getName(context.player(), currency.getKey());
                Component formatted = localizations.format(context.player(), currency.getKey(), amount);

                result.add(new ExecutionDescription(
                    "",
                    Map.of(
                        "amount",
                        Component.text(amount.toString()),
                        "formatted_amount",
                        Component.text(localizations.formatCompact(amount)),
                        "currency",
                        name,
                        "currency_key",
                        Component.text(currency.getKey().toString())
                    ),
                    formatted
                ));
            });

            return List.copyOf(result);
        }

        private Map<Currency, WholeAmount> evaluate(final PlayerExecutionContext context) {
            Map<Currency, WholeAmount> result = new LinkedHashMap<>();

            amounts.forEach((currency, expression) -> {
                BigDecimal value = expression.evaluateDecimal(context);

                if (value.signum() <= 0) {
                    throw new IllegalStateException("Currency reward amount for " + currency.getKey()
                        + " must evaluate to a positive whole number");
                }

                try {
                    result.put(currency, WholeAmount.of(value.toBigIntegerExact()));
                } catch (ArithmeticException exception) {
                    throw new IllegalStateException(
                        "Currency reward amount for " + currency.getKey() + " must evaluate to a positive whole number",
                        exception
                    );
                }
            });

            return Map.copyOf(result);
        }
    }
}
