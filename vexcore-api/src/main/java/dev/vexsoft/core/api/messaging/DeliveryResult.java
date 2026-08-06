package dev.vexsoft.core.api.messaging;

/** Reports whether a network message was sent, queued, or rejected */
public enum DeliveryResult {
  SENT,
  QUEUED,
  NO_CONNECTION,
  MESSAGE_TOO_LARGE,
  FAILED
}
