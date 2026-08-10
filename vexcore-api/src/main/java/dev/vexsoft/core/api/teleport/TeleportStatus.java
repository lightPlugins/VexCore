package dev.vexsoft.core.api.teleport;

/** Describes the final outcome of a local or cross-server teleport. */
public enum TeleportStatus {
  SUCCESS,
  PLAYER_OFFLINE,
  WORLD_NOT_LOADED,
  SERVER_UNAVAILABLE,
  TELEPORT_REJECTED,
  TRANSFER_REJECTED,
  TIMED_OUT,
  FAILED
}
