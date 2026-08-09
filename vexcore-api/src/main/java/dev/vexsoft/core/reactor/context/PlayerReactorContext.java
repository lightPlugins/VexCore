package dev.vexsoft.core.reactor.context;

import dev.vexsoft.core.api.player.VexPlayer;

/** Exposes the loaded Vex player associated with a reaction invocation. */
public interface PlayerReactorContext extends ReactorContext {

  /** Returns the loaded player session. */
  VexPlayer getPlayer();
}
