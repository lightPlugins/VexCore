package dev.vexsoft.core.api.service.requirement;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.execution.TypedExecutionDescription;
import dev.vexsoft.core.requirement.CompiledRequirements;
import dev.vexsoft.core.requirement.RequirementExecutionResult;
import java.util.List;
import net.kyori.adventure.text.Component;

/** Compiles, tests, and presents extensible requirement sections. */
public interface RequirementService extends VexService {

  /** Compiles every direct key in a requirement section. */
  CompiledRequirements compile(ConfigurationSection section);

  /** Tests all configured requirements using AND semantics. */
  RequirementExecutionResult test(
      CompiledRequirements requirements,
      PlayerExecutionContext context
  );

  /** Renders every configured requirement and its current state. */
  List<Component> describe(
      CompiledRequirements requirements,
      PlayerExecutionContext context
  );

  /** Returns typed localization-ready requirement lines and their current states. */
  List<TypedExecutionDescription> present(
      CompiledRequirements requirements,
      PlayerExecutionContext context
  );
}
