package dev.vexsoft.core.api.messaging;

/** Defines where Velocity should deliver a network message */
public enum MessageTargetType {
  PROXY,
  SERVER,
  PLAYER,
  ALL_SERVERS
}
