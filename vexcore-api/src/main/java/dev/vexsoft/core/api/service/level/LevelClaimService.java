package dev.vexsoft.core.api.service.level;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.level.CompiledLevelDefinition;
import dev.vexsoft.core.level.LevelProgressAccess;
import dev.vexsoft.core.level.LevelState;
import dev.vexsoft.core.level.claim.LevelClaimBatchResult;
import dev.vexsoft.core.level.claim.LevelClaimPreview;
import dev.vexsoft.core.level.claim.LevelClaimResult;
import dev.vexsoft.core.reward.RewardContributions;
import java.util.Map;

/** Previews and executes sequential manual or automatic level claims. */
public interface LevelClaimService extends VexService {

  /** Calculates available and claimed progression without mutation. */
  LevelState getState(
      VexPlayer player,
      LevelProgressAccess<?> progress,
      CompiledLevelDefinition definition
  );

  /** Previews the next sequential claim. */
  LevelClaimPreview previewNext(
      VexPlayer player,
      LevelProgressAccess<?> progress,
      CompiledLevelDefinition definition,
      Map<String, Object> variables
  );

  /** Attempts the next sequential claim. */
  LevelClaimResult claimNext(
      VexPlayer player,
      LevelProgressAccess<?> progress,
      CompiledLevelDefinition definition,
      Map<String, Object> variables
  );

  /** Claims sequential levels until none remain or one attempt is blocked. */
  LevelClaimBatchResult claimAvailable(
      VexPlayer player,
      LevelProgressAccess<?> progress,
      CompiledLevelDefinition definition,
      Map<String, Object> variables
  );

  /** Processes reached levels only when the compiled claim mode is automatic. */
  LevelClaimBatchResult processAutomaticClaims(
      VexPlayer player,
      LevelProgressAccess<?> progress,
      CompiledLevelDefinition definition,
      Map<String, Object> variables
  );

  /** Reconstructs all contribution rewards through the currently claimed level. */
  RewardContributions calculateContributions(
      VexPlayer player,
      LevelProgressAccess<?> progress,
      CompiledLevelDefinition definition,
      Map<String, Object> variables
  );
}
