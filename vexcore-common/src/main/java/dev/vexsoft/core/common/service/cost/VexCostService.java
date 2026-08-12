package dev.vexsoft.core.common.service.cost;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.cost.CostService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.execution.ExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.service.execution.ExecutionComponentKind;
import dev.vexsoft.core.cost.CompiledCosts;
import dev.vexsoft.core.cost.Cost;
import dev.vexsoft.core.cost.CostCheckResult;
import dev.vexsoft.core.cost.CostConsumeResult;
import dev.vexsoft.core.cost.CostExecutionResult;
import dev.vexsoft.core.cost.CostPayment;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.execution.TypedExecutionDescription;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Default registry-backed cost compiler with compensating multi-cost consumption. */
@Dependencies(ExecutionComponentCoordinatorService.class)
public final class VexCostService implements CostService {

  private final ExecutionComponentCoordinatorService components;

  /** Captures the shared component registry. */
  public VexCostService(final VexServiceRegistry services) {
    components = Objects.requireNonNull(services, "services")
        .require(ExecutionComponentCoordinatorService.class);
  }

  @Override
  public CompiledCosts compile(final ConfigurationSection section) {
    ConfigurationSection checked = Objects.requireNonNull(section, "section");
    List<CompiledCosts.Entry> entries = new ArrayList<>();
    for (String key : checked.getKeys(false)) {
      Cost cost = components.find(ExecutionComponentKind.COST, key)
          .map(Cost.class::cast)
          .orElseThrow(() -> new IllegalArgumentException("Unknown cost key: " + key));
      try {
        entries.add(new CompiledCosts.Entry(key, cost.compile(checked.get(key))));
      } catch (RuntimeException exception) {
        throw new IllegalArgumentException("Invalid cost '" + key + "'", exception);
      }
    }
    return new CompiledCosts(entries);
  }

  @Override
  public CostExecutionResult check(
      final CompiledCosts costs,
      final PlayerExecutionContext context
  ) {
    List<CostExecutionResult.Entry> results = new ArrayList<>();
    boolean affordable = true;
    for (CompiledCosts.Entry entry : costs.entries()) {
      CostCheckResult result = entry.cost().check(context);
      results.add(new CostExecutionResult.Entry(entry.key(), result.affordable(), result.message()));
      affordable &= result.affordable();
    }
    return new CostExecutionResult(affordable, results);
  }

  @Override
  public CostExecutionResult consume(
      final CompiledCosts costs,
      final PlayerExecutionContext context
  ) {
    return pay(costs, context).result();
  }

  @Override
  public CostPayment pay(
      final CompiledCosts costs,
      final PlayerExecutionContext context
  ) {
    CostExecutionResult checked = check(costs, context);
    if (!checked.successful()) {
      return new CostPayment(false, checked, List.of());
    }
    List<CostPayment.Entry> consumed = new ArrayList<>();
    List<CostExecutionResult.Entry> results = new ArrayList<>();
    for (CompiledCosts.Entry entry : costs.entries()) {
      CostConsumeResult result = entry.cost().consume(context);
      results.add(new CostExecutionResult.Entry(entry.key(), result.successful(), result.message()));
      if (!result.successful()) {
        refund(consumed, context);
        return new CostPayment(false, new CostExecutionResult(false, results), List.of());
      }
      result.getReceipt().ifPresent(
          receipt -> consumed.add(new CostPayment.Entry(entry.cost(), receipt))
      );
    }
    return new CostPayment(true, new CostExecutionResult(true, results), consumed);
  }

  @Override
  public void refund(
      final CostPayment payment,
      final PlayerExecutionContext context
  ) {
    refund(Objects.requireNonNull(payment, "payment").entries(), context);
  }

  @Override
  public List<Component> describe(
      final CompiledCosts costs,
      final PlayerExecutionContext context
  ) {
    return costs.entries().stream().map(entry -> entry.cost().describe(context)).toList();
  }

  @Override
  public List<TypedExecutionDescription> present(
      final CompiledCosts costs,
      final PlayerExecutionContext context
  ) {
    return costs.entries().stream()
        .flatMap(entry -> entry.cost().describeEntries(context).stream()
            .map(description -> TypedExecutionDescription.of(entry.key(), description)))
        .toList();
  }

  private static void refund(
      final List<CostPayment.Entry> consumed,
      final PlayerExecutionContext context
  ) {
    for (int index = consumed.size() - 1; index >= 0; index--) {
      CostPayment.Entry entry = consumed.get(index);
      entry.cost().refund(context, entry.receipt());
    }
  }
}
