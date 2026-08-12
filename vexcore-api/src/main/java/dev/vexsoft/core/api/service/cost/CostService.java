package dev.vexsoft.core.api.service.cost;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.cost.CompiledCosts;
import dev.vexsoft.core.cost.CostExecutionResult;
import dev.vexsoft.core.cost.CostPayment;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.execution.TypedExecutionDescription;
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

  /** Consumes all costs while retaining receipts for a later transaction rollback. */
  CostPayment pay(CompiledCosts costs, PlayerExecutionContext context);

  /** Refunds a previously successful payment in reverse order. */
  void refund(CostPayment payment, PlayerExecutionContext context);

  /** Renders every configured cost. */
  List<Component> describe(CompiledCosts costs, PlayerExecutionContext context);

  /** Returns typed localization-ready cost lines. */
  List<TypedExecutionDescription> present(CompiledCosts costs, PlayerExecutionContext context);
}
