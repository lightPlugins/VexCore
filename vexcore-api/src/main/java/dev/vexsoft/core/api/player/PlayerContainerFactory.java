package dev.vexsoft.core.api.player;

/** Creates one feature container for a loaded Vex player. */
@FunctionalInterface
public interface PlayerContainerFactory<T extends PlayerContainer> {

  /** Creates a container bound to the supplied player session. */
  T create(VexPlayer player);
}
