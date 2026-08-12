package dev.vexsoft.core.common.service.level;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.cost.CostService;
import dev.vexsoft.core.api.service.level.LevelClaimService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.requirement.RequirementService;
import dev.vexsoft.core.api.service.reward.RewardService;
import dev.vexsoft.core.cost.CompiledCosts;
import dev.vexsoft.core.cost.CostExecutionResult;
import dev.vexsoft.core.cost.CostPayment;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.level.ClaimedLevelOverflowPolicy;
import dev.vexsoft.core.level.CompiledLevelDefinition;
import dev.vexsoft.core.level.CompiledLevelRule;
import dev.vexsoft.core.level.LevelClaimMode;
import dev.vexsoft.core.level.LevelProgress;
import dev.vexsoft.core.level.LevelProgressAccess;
import dev.vexsoft.core.level.LevelSnapshot;
import dev.vexsoft.core.level.LevelState;
import dev.vexsoft.core.level.claim.LevelClaimBatchResult;
import dev.vexsoft.core.level.claim.LevelClaimPreview;
import dev.vexsoft.core.level.claim.LevelClaimResult;
import dev.vexsoft.core.level.claim.LevelClaimStatus;
import dev.vexsoft.core.requirement.CompiledRequirements;
import dev.vexsoft.core.requirement.RequirementExecutionResult;
import dev.vexsoft.core.reward.CompiledRewards;
import dev.vexsoft.core.reward.RewardContributions;
import dev.vexsoft.core.reward.RewardExecutionReport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Default sequential claim runtime shared by progression plugins. */
@Dependencies({RequirementService.class, CostService.class, RewardService.class})
public final class VexLevelClaimService implements LevelClaimService {

  private final RequirementService requirements;
  private final CostService costs;
  private final RewardService rewards;

  /** Captures the shared execution services. */
  public VexLevelClaimService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
    requirements = services.require(RequirementService.class);
    costs = services.require(CostService.class);
    rewards = services.require(RewardService.class);
  }

  @Override
  public LevelState getState(
      final VexPlayer player,
      final LevelProgressAccess<?> progressAccess,
      final CompiledLevelDefinition definition
  ) {
    LevelProgress progress = Objects.requireNonNull(progressAccess, "progress").read(player);
    return state(progress, definition);
  }

  private static LevelState state(
      final LevelProgress progress,
      final CompiledLevelDefinition definition
  ) {
    Objects.requireNonNull(definition, "definition");
    LevelSnapshot available = definition.curve().calculate(progress.getExperience());
    int claimed = progress.getClaimedLevel();
    if (claimed < definition.curve().getMinimumLevel()) {
      throw new IllegalArgumentException("claimed level is below the curve minimum");
    }
    if (definition.overflowPolicy() == ClaimedLevelOverflowPolicy.CLAMP) {
      claimed = Math.min(claimed, available.level());
    }
    return new LevelState(available, claimed, claimed + 1);
  }

  @Override
  public LevelClaimPreview previewNext(
      final VexPlayer player,
      final LevelProgressAccess<?> progressAccess,
      final CompiledLevelDefinition definition,
      final Map<String, Object> variables
  ) {
    LevelProgress progress = progressAccess.read(player);
    return preview(player, progress, definition, variables);
  }

  private LevelClaimPreview preview(
      final VexPlayer player,
      final LevelProgress progress,
      final CompiledLevelDefinition definition,
      final Map<String, Object> variables
  ) {
    LevelState state = state(progress, definition);
    int level = state.nextClaimLevel();
    CompiledParts parts = merge(definition.getRules(level));
    PlayerExecutionContext context = context(player, progress, state, level, variables);
    RequirementExecutionResult requirementResult = requirements.test(parts.requirements(), context);
    CostExecutionResult costResult = costs.check(parts.costs(), context);
    LevelClaimStatus status;
    if (level > definition.curve().getMaximumLevel() || level > state.available().level()) {
      status = LevelClaimStatus.NOT_AVAILABLE;
    } else if (!requirementResult.satisfied()) {
      status = LevelClaimStatus.REQUIREMENTS_NOT_MET;
    } else if (!costResult.successful()) {
      status = LevelClaimStatus.COSTS_NOT_AFFORDABLE;
    } else {
      status = LevelClaimStatus.READY;
    }
    return new LevelClaimPreview(
        level,
        state,
        status,
        requirementResult,
        costResult,
        rewards.describe(parts.rewards(), context),
        costs.describe(parts.costs(), context),
        requirements.describe(parts.requirements(), context),
        rewards.present(parts.rewards(), context),
        costs.present(parts.costs(), context),
        requirements.present(parts.requirements(), context)
    );
  }

  @Override
  public LevelClaimResult claimNext(
      final VexPlayer player,
      final LevelProgressAccess<?> progressAccess,
      final CompiledLevelDefinition definition,
      final Map<String, Object> variables
  ) {
    synchronized (player) {
      LevelProgress progress = progressAccess.read(player);
      LevelClaimPreview preview = preview(player, progress, definition, variables);
      if (!preview.isClaimable()) {
        return result(preview, preview.status(), emptyRewards());
      }
      int level = preview.level();
      CompiledParts parts = merge(definition.getRules(level));
      PlayerExecutionContext context = context(
          player, progress, preview.state(), level, variables
      );
      CostPayment payment = costs.pay(parts.costs(), context);
      if (!payment.successful()) {
        return result(preview, LevelClaimStatus.COST_FAILED, emptyRewards());
      }
      RewardExecutionReport granted = rewards.grantActions(parts.rewards(), context);
      if (!granted.isSuccessful()) {
        costs.refund(payment, context);
        return result(preview, LevelClaimStatus.REWARD_FAILED, granted);
      }
      progressAccess.updateClaimedLevel(player, level);
      return result(preview, LevelClaimStatus.CLAIMED, granted);
    }
  }

  @Override
  public LevelClaimBatchResult claimAvailable(
      final VexPlayer player,
      final LevelProgressAccess<?> progressAccess,
      final CompiledLevelDefinition definition,
      final Map<String, Object> variables
  ) {
    List<LevelClaimResult> results = new ArrayList<>();
    while (getState(player, progressAccess, definition).hasClaimableLevel()) {
      LevelClaimResult result = claimNext(player, progressAccess, definition, variables);
      results.add(result);
      if (!result.isSuccessful()) {
        break;
      }
    }
    return new LevelClaimBatchResult(results);
  }

  @Override
  public LevelClaimBatchResult processAutomaticClaims(
      final VexPlayer player,
      final LevelProgressAccess<?> progressAccess,
      final CompiledLevelDefinition definition,
      final Map<String, Object> variables
  ) {
    if (definition.claimMode() != LevelClaimMode.AUTOMATIC) {
      return new LevelClaimBatchResult(List.of());
    }
    return claimAvailable(player, progressAccess, definition, variables);
  }

  @Override
  public RewardContributions calculateContributions(
      final VexPlayer player,
      final LevelProgressAccess<?> progressAccess,
      final CompiledLevelDefinition definition,
      final Map<String, Object> variables
  ) {
    LevelProgress progress = progressAccess.read(player);
    LevelState state = state(progress, definition);
    int maximum = Math.min(state.claimedLevel(), definition.curve().getMaximumLevel());
    List<RewardService.RewardInvocation> invocations = new ArrayList<>();
    for (int level = definition.curve().getMinimumLevel() + 1; level <= maximum; level++) {
      PlayerExecutionContext context = context(player, progress, state, level, variables);
      for (CompiledLevelRule rule : definition.getRules(level)) {
        invocations.add(new RewardService.RewardInvocation(rule.rewards(), context));
      }
    }
    return rewards.calculateContributions(invocations);
  }

  private static LevelClaimResult result(
      final LevelClaimPreview preview,
      final LevelClaimStatus status,
      final RewardExecutionReport rewards
  ) {
    return new LevelClaimResult(preview.level(), status, preview, rewards);
  }

  private static RewardExecutionReport emptyRewards() {
    return new RewardExecutionReport(Map.of());
  }

  private static PlayerExecutionContext context(
      final VexPlayer player,
      final LevelProgress progress,
      final LevelState state,
      final int level,
      final Map<String, Object> variables
  ) {
    Map<String, Object> values = new LinkedHashMap<>(
        Objects.requireNonNullElse(variables, Map.of())
    );
    values.put("level", level);
    values.put("experience", progress.getExperience());
    values.put("available-level", state.available().level());
    values.put("claimed-level", state.claimedLevel());
    return new PlayerExecutionContext(Objects.requireNonNull(player, "player"), values);
  }

  private static CompiledParts merge(final List<CompiledLevelRule> rules) {
    List<CompiledRequirements.Entry> requirementEntries = new ArrayList<>();
    List<CompiledCosts.Entry> costEntries = new ArrayList<>();
    List<CompiledRewards.Entry> rewardEntries = new ArrayList<>();
    for (CompiledLevelRule rule : rules) {
      requirementEntries.addAll(rule.requirements().entries());
      costEntries.addAll(rule.costs().entries());
      rewardEntries.addAll(rule.rewards().entries());
    }
    return new CompiledParts(
        new CompiledRequirements(requirementEntries),
        new CompiledCosts(costEntries),
        new CompiledRewards(rewardEntries)
    );
  }

  private record CompiledParts(
      CompiledRequirements requirements,
      CompiledCosts costs,
      CompiledRewards rewards
  ) {}
}
