package dev.vexsoft.core.paper.dialogs;

/**
 * Describes how a dialog session finished
 */
public enum DialogResultType {
  CONFIRMED,
  CANCELLED,
  CLOSED,
  REPLACED,
  PLAYER_LEFT,
  PLUGIN_DISABLED,
  TIMED_OUT,
  UNAVAILABLE
}
