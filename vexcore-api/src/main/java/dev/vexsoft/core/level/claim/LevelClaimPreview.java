package dev.vexsoft.core.level.claim;

import dev.vexsoft.core.cost.CostExecutionResult;
import dev.vexsoft.core.level.LevelState;
import dev.vexsoft.core.requirement.RequirementExecutionResult;
import java.util.List;
import dev.vexsoft.core.execution.TypedExecutionDescription;
import net.kyori.adventure.text.Component;

/** Non-mutating live preview of the next sequential level claim. */
public record LevelClaimPreview(
    int level,
    LevelState state,
    LevelClaimStatus status,
    RequirementExecutionResult requirements,
    CostExecutionResult costs,
    List<Component> rewardLines,
    List<Component> costLines,
    List<Component> requirementLines,
    List<TypedExecutionDescription> rewards,
    List<TypedExecutionDescription> presentedCosts,
    List<TypedExecutionDescription> presentedRequirements
) {

  /** Copies all presentation lines. */
  public LevelClaimPreview {
    rewardLines = List.copyOf(rewardLines);
    costLines = List.copyOf(costLines);
    requirementLines = List.copyOf(requirementLines);
    rewards = List.copyOf(rewards);
    presentedCosts = List.copyOf(presentedCosts);
    presentedRequirements = List.copyOf(presentedRequirements);
  }

  /** Returns whether the previewed level can be claimed now. */
  public boolean isClaimable() {
    return status == LevelClaimStatus.READY;
  }
}
