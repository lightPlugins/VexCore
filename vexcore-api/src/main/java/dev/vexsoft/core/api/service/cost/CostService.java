package dev.vexsoft.core.api.service.cost;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.cost.CompiledCosts;
import dev.vexsoft.core.cost.CostExecutionResult;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import java.util.List;
import net.kyori.adventure.text.Component;

/** Compiles, checks, consumes, and presents extensible cost sections. */
public interface CostService extends VexService {

  /** Compiles every direct key in a cost section. */
  CompiledCosts compile(ConfigurationSection section);

  /** Checks all costs without mutation. */
  CostExecutionResult check(CompiledCosts costs, PlayerExecutionContext context);

  /** Consumes all costs and compensates completed entries if a later entry fails. */
  CostExecutionResult consume(CompiledCosts costs, PlayerExecutionContext context);

  /** Renders every configured cost. */
  List<Component> describe(CompiledCosts costs, PlayerExecutionContext context);
}
