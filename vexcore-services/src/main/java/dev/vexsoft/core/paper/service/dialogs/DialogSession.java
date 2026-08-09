package dev.vexsoft.core.paper.service.dialogs;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.paper.dialogs.DialogResult;
import dev.vexsoft.core.paper.dialogs.DialogResultType;
import dev.vexsoft.core.paper.scheduler.VexTask;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.Setter;

/**
 * Tracks one active dialog and protects its result from duplicate callbacks
 */
@Getter
public final class DialogSession<T> {

  private final ServiceOwner owner;
  private final UUID playerId;
  private final UUID sessionId = UUID.randomUUID();
  private final CompletableFuture<DialogResult<T>> future = new CompletableFuture<>();
  private final AtomicBoolean finished = new AtomicBoolean();
  @Setter
  private VexTask timeoutTask;

  public DialogSession(final ServiceOwner owner, final UUID playerId) {
    this.owner = owner;
    this.playerId = playerId;
  }

  public boolean finish(final DialogResult<T> result) {
    if (!finished.compareAndSet(false, true)) {
      return false;
    }
    if (timeoutTask != null) {
      timeoutTask.cancel();
    }
    future.complete(result);
    return true;
  }

  public boolean finishWithoutValue(final DialogResultType type) {
    return finish(DialogResult.empty(type));
  }
}
