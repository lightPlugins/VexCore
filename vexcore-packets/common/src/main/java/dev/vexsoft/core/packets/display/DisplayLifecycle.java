package dev.vexsoft.core.packets.display;

/** Player lifecycle transitions that can automatically destroy a fake display. */
public enum DisplayLifecycle {
  PLAYER_QUIT,
  PLAYER_DEATH,
  WORLD_CHANGE
}
