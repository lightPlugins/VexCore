package dev.vexsoft.core.api.player;

import org.jetbrains.annotations.ApiStatus;

/** Resolves registered container types to dense player-local array slots. */
@ApiStatus.Internal
@FunctionalInterface
public interface PlayerContainerLookup {

  /** Returns the registered slot, or {@code -1} when the type is unknown. */
  int findSlot(Class<? extends PlayerContainer> type);
}
