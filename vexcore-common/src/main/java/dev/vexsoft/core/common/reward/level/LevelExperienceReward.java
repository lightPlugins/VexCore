package dev.vexsoft.core.common.reward.level;

import dev.vexsoft.core.api.service.expression.ExpressionService;
import dev.vexsoft.core.api.service.level.LevelInstanceService;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.level.LevelExecutionText;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.expression.CompiledExpression;
import dev.vexsoft.core.reward.CompiledReward;
import dev.vexsoft.core.reward.Reward;
import dev.vexsoft.core.reward.RewardBehavior;
import dev.vexsoft.core.reward.RewardResult;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Adds XP to a configured level instance through the common reward pipeline. */
@Dependencies({LevelInstanceService.class, ExpressionService.class, LocalizationService.class})
public final class LevelExperienceReward implements Reward {

    private final LevelInstanceService levels;
    private final ExpressionService expressions;
    private final LocalizationService localizations;

    public LevelExperienceReward(final VexServiceRegistry services) {
        levels = services.require(LevelInstanceService.class);
        expressions = services.require(ExpressionService.class);
        localizations = services.require(LocalizationService.class);
    }

    @Override
    public CompiledReward compile(final Object value) {
        Map<?, ?> configuration = LevelExecutionText.values(value);
        String id = Objects.toString(configuration.get("system"), "");
        levels.validateReference(id, 0);
        var amount = expressions.compile(Objects.toString(configuration.get("amount"), ""));
        return new Compiled(id, amount, levels, localizations);
    }

    private record Compiled(String id, CompiledExpression amount, LevelInstanceService levels,
                            LocalizationService localizations) implements CompiledReward {

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
            levels.addExperience(context.player(), id, evaluate(context));
            return RewardResult.success();
        }

        @Override
        public Component describe(final PlayerExecutionContext context) {
            return LevelExecutionText.render(
                localizations, levels, context, id, "levels.reward",
                Map.of("amount", BigDecimal.valueOf(evaluate(context)).stripTrailingZeros().toPlainString())
            );
        }

        private double evaluate(final PlayerExecutionContext context) {
            double value = amount.evaluateNumber(context);
            if (!Double.isFinite(value) || value <= 0) {
                throw new IllegalArgumentException("Level XP reward must be finite and positive");
            }
            return value;
        }
    }
}
