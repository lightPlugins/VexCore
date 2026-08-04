package dev.vexsoft.core.packets.hologram;

/**
 * Handles an interaction with a viewer-specific hologram
 */
@FunctionalInterface
public interface HologramInteractHandler {

  /** Handles the decoded interaction on the viewer's entity thread */
  public void handle(HologramInteraction interaction);
}
