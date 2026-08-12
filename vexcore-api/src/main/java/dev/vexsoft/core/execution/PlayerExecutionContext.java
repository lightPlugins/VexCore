package dev.vexsoft.core.execution;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.expression.PlayerEvaluationContext;
import java.util.Map;
import java.util.Objects;

/** Immutable player context shared by rewards, costs, and requirements. */
public record PlayerExecutionContext(VexPlayer player, Map<String, Object> variables)
    implements PlayerEvaluationContext {

  /** Copies the supplied variables and validates the player. */
  public PlayerExecutionContext {
    Objects.requireNonNull(player, "player");
    variables = Map.copyOf(Objects.requireNonNull(variables, "variables"));
  }

  @Override
  public Object getVariable(final String name) {
    return variables.get(Objects.requireNonNull(name, "name"));
  }

  @Override
  public VexPlayer getPlayer() {
    return player;
  }
}
