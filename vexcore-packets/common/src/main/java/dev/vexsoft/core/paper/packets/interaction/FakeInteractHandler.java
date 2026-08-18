package dev.vexsoft.core.paper.packets.interaction;

/** Receives viewer input directed at one virtual interaction entity. */
@FunctionalInterface
public interface FakeInteractHandler {

  /**
   * Handles one left- or right-click interaction on the owning player's scheduler.
   *
   * @param interaction decoded viewer input and interaction identity
   */
  void handle(FakeInteraction interaction);
}
