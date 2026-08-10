package dev.vexsoft.core.api.teleport;

import java.util.Objects;

/** Provides a structured teleport outcome and an end-user-safe explanation. */
public record TeleportResult(TeleportStatus status, String message) {

  /** Creates a validated result. */
  public TeleportResult {
    status = Objects.requireNonNull(status, "status");
    message = Objects.requireNonNull(message, "message");
  }

  /** Returns a successful teleport result. */
  public static TeleportResult success() {
    return new TeleportResult(TeleportStatus.SUCCESS, "Teleport completed successfully");
  }

  /** Returns whether the teleport completed successfully. */
  public boolean isSuccess() {
    return status == TeleportStatus.SUCCESS;
  }
}
