package dev.vexsoft.core.gameplay.reactor;

import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.gameplay.reactor.context.ReactorContext;
import java.util.Collection;

/** Compiles owner reactions atomically and dispatches registered runtime triggers. */
public interface ReactorEngine extends VexService {

  /**
   * Compiles and atomically replaces every reaction belonging to this service owner.
   *
   * @param definitions complete desired reaction set
   * @throws RuntimeException when any definition is invalid; the previous set remains active
   */
  void reload(Collection<ReactionDefinition> definitions);

  /** Dispatches a trigger invocation to the current immutable runtime snapshot. */
  void dispatch(String triggerId, ReactorContext context);

  /** Removes every reaction belonging to this service owner. */
  void clear();
}
