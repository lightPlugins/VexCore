package dev.vexsoft.core.expression;

import dev.vexsoft.core.api.player.VexPlayer;

/** Expression context associated with one loaded player. */
public interface PlayerEvaluationContext extends EvaluationContext {

  /** Returns the loaded player session. */
  VexPlayer getPlayer();
}
