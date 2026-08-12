package dev.vexsoft.core.common.service.requirement;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.requirement.RequirementService;
import dev.vexsoft.core.common.service.execution.ExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.service.execution.ExecutionComponentKind;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.execution.TypedExecutionDescription;
import dev.vexsoft.core.requirement.CompiledRequirements;
import dev.vexsoft.core.requirement.Requirement;
import dev.vexsoft.core.requirement.RequirementExecutionResult;
import dev.vexsoft.core.requirement.RequirementResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Default registry-backed requirement compiler and evaluator. */
@Dependencies(ExecutionComponentCoordinatorService.class)
public final class VexRequirementService implements RequirementService {

  private final ExecutionComponentCoordinatorService components;

  /** Captures the shared component registry. */
  public VexRequirementService(final VexServiceRegistry services) {
    components = Objects.requireNonNull(services, "services")
        .require(ExecutionComponentCoordinatorService.class);
  }

  @Override
  public CompiledRequirements compile(final ConfigurationSection section) {
    ConfigurationSection checked = Objects.requireNonNull(section, "section");
    List<CompiledRequirements.Entry> entries = new ArrayList<>();
    for (String key : checked.getKeys(false)) {
      Requirement requirement = components.find(ExecutionComponentKind.REQUIREMENT, key)
          .map(Requirement.class::cast)
          .orElseThrow(() -> new IllegalArgumentException("Unknown requirement key: " + key));
      try {
        entries.add(new CompiledRequirements.Entry(key, requirement.compile(checked.get(key))));
      } catch (RuntimeException exception) {
        throw new IllegalArgumentException("Invalid requirement '" + key + "'", exception);
      }
    }
    return new CompiledRequirements(entries);
  }

  @Override
  public RequirementExecutionResult test(
      final CompiledRequirements requirements,
      final PlayerExecutionContext context
  ) {
    Map<String, RequirementResult> results = new LinkedHashMap<>();
    boolean satisfied = true;
    for (CompiledRequirements.Entry entry : requirements.entries()) {
      RequirementResult result = entry.requirement().test(context);
      results.put(entry.key(), result);
      satisfied &= result.satisfied();
    }
    return new RequirementExecutionResult(satisfied, results);
  }

  @Override
  public List<Component> describe(
      final CompiledRequirements requirements,
      final PlayerExecutionContext context
  ) {
    return requirements.entries().stream()
        .map(entry -> entry.requirement().describe(context))
        .toList();
  }

  @Override
  public List<TypedExecutionDescription> present(
      final CompiledRequirements requirements,
      final PlayerExecutionContext context
  ) {
    return requirements.entries().stream()
        .flatMap(entry -> entry.requirement().describeEntries(context).stream()
            .map(description -> TypedExecutionDescription.of(entry.key(), description)))
        .toList();
  }
}
