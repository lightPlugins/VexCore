package dev.vexsoft.core.common.service.level;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.cost.CostService;
import dev.vexsoft.core.api.service.expression.ExpressionService;
import dev.vexsoft.core.api.service.level.LevelService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.requirement.RequirementService;
import dev.vexsoft.core.api.service.reward.RewardService;
import dev.vexsoft.core.common.configuration.ConfigurateConfigurationSection;
import dev.vexsoft.core.cost.CompiledCosts;
import dev.vexsoft.core.expression.CompiledExpression;
import dev.vexsoft.core.expression.EvaluationContext;
import dev.vexsoft.core.level.ClaimedLevelOverflowPolicy;
import dev.vexsoft.core.level.CompiledLevelDefinition;
import dev.vexsoft.core.level.CompiledLevelRule;
import dev.vexsoft.core.level.LevelClaimMode;
import dev.vexsoft.core.level.LevelDefinition;
import dev.vexsoft.core.level.LevelRuleDefinition;
import dev.vexsoft.core.requirement.CompiledRequirements;
import dev.vexsoft.core.reward.CompiledRewards;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Default compiler for expression-based level definitions. */
@Dependencies({ExpressionService.class, RequirementService.class, CostService.class, RewardService.class})
public final class VexLevelService implements LevelService {

  private final ExpressionService expressions;
  private final RequirementService requirements;
  private final CostService costs;
  private final RewardService rewards;

  /** Captures the shared execution services. */
  public VexLevelService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
    expressions = services.require(ExpressionService.class);
    requirements = services.require(RequirementService.class);
    costs = services.require(CostService.class);
    rewards = services.require(RewardService.class);
  }

  @Override
  public CompiledLevelDefinition compile(final LevelDefinition definition) {
    Objects.requireNonNull(definition, "definition");
    CompiledExpression required = expressions.compile(definition.requiredExperience());
    int count = definition.maximumLevel() - definition.minimumLevel() + 1;
    double[] thresholds = new double[count];
    for (int index = 1; index < count; index++) {
      int targetLevel = definition.minimumLevel() + index;
      double amount = required.evaluateNumber(new EvaluationContext() {
        @Override
        public Object getVariable(final String name) {
          return "level".equals(name) ? targetLevel : null;
        }
      });
      if (!Double.isFinite(amount) || amount <= 0.0D) {
        throw new IllegalArgumentException(
            "Required experience for level " + targetLevel + " must be finite and greater than zero"
        );
      }
      thresholds[index] = thresholds[index - 1] + amount;
      if (!Double.isFinite(thresholds[index])) {
        throw new IllegalArgumentException("Level experience curve exceeds the supported number range");
      }
    }

    List<CompiledLevelRule> compiledRules = new ArrayList<>();
    for (LevelRuleDefinition rule : definition.rules()) {
      if (rule.minimumLevel() <= definition.minimumLevel()
          || rule.minimumLevel() > definition.maximumLevel()) {
        throw new IllegalArgumentException("Rule minimum level is outside the claimable level range");
      }
      compiledRules.add(new CompiledLevelRule(
          rule.minimumLevel(),
          rule.step(),
          rule.requirements().isEmpty() ? new CompiledRequirements(List.of())
              : requirements.compile(ConfigurateConfigurationSection.from(rule.requirements())),
          rule.costs().isEmpty() ? new CompiledCosts(List.of())
              : costs.compile(ConfigurateConfigurationSection.from(rule.costs())),
          rule.rewards().isEmpty() ? new CompiledRewards(List.of())
              : rewards.compile(ConfigurateConfigurationSection.from(rule.rewards()))
      ));
    }
    return new CompiledLevelDefinition(
        new VexCompiledLevelCurve(
            definition.minimumLevel(), definition.maximumLevel(), thresholds
        ),
        definition.claimMode(),
        definition.overflowPolicy(),
        compiledRules
    );
  }

  @Override
  public CompiledLevelDefinition compile(final ConfigurationSection configuration) {
    Objects.requireNonNull(configuration, "configuration");
    ConfigurationSection leveling = requireSection(configuration, "leveling");
    int minimum = leveling.getInt("min-level", 0);
    int maximum = leveling.getInt("max-level", 100);
    String expression = Objects.requireNonNull(
        leveling.getString("experience.required"), "Missing leveling.experience.required"
    );
    LevelClaimMode mode = parseEnum(
        LevelClaimMode.class, leveling.getString("claim-mode", "MANUAL")
    );
    ClaimedLevelOverflowPolicy overflow = parseEnum(
        ClaimedLevelOverflowPolicy.class,
        leveling.getString("claimed-level-overflow", "KEEP")
    );
    List<LevelRuleDefinition> rules = new ArrayList<>();
    Object rawRules = configuration.get("levels");
    if (rawRules instanceof List<?> list) {
      for (Object rawRule : list) {
        if (!(rawRule instanceof Map<?, ?> map)) {
          throw new IllegalArgumentException("Every levels entry must be a map");
        }
        ConfigurationSection rule = ConfigurateConfigurationSection.from(map);
        rules.add(new LevelRuleDefinition(
            rule.getInt("min-level", 1),
            rule.getInt("step", 1),
            values(rule.getSection("requirements")),
            values(rule.getSection("costs")),
            values(rule.getSection("rewards"))
        ));
      }
    } else if (rawRules != null) {
      throw new IllegalArgumentException("levels must be a list");
    }
    return compile(new LevelDefinition(minimum, maximum, expression, mode, overflow, rules));
  }

  private static ConfigurationSection requireSection(
      final ConfigurationSection configuration,
      final String path
  ) {
    ConfigurationSection section = configuration.getSection(path);
    if (section == null) {
      throw new IllegalArgumentException("Missing configuration section: " + path);
    }
    return section;
  }

  private static Map<String, Object> values(final ConfigurationSection section) {
    return section == null ? Map.of() : section.getValues(false);
  }

  private static <T extends Enum<T>> T parseEnum(
      final Class<T> type,
      final String value
  ) {
    try {
      return Enum.valueOf(type, value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Unsupported " + type.getSimpleName() + ": " + value, exception);
    }
  }
}
