package dev.vexsoft.core.paper.dialog;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.dialog.DialogResult;
import dev.vexsoft.core.dialog.DialogResultType;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Dependencies
public final class VexDialogCoordinatorService implements DialogCoordinatorService, AutoCloseable {

  private final Map<UUID, DialogSession<?>> sessions = new ConcurrentHashMap<>();

  public VexDialogCoordinatorService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public <T> DialogSession<T> begin(final ServiceOwner owner, final UUID playerId) {
    DialogSession<T> session = new DialogSession<>(
        Objects.requireNonNull(owner, "owner"),
        Objects.requireNonNull(playerId, "playerId")
    );
    DialogSession<?> replaced = sessions.put(playerId, session);
    if (replaced != null) {
      replaced.finishWithoutValue(DialogResultType.REPLACED);
    }
    return session;
  }

  @Override
  public <T> boolean complete(
      final DialogSession<T> session,
      final DialogResult<T> result
  ) {
    Objects.requireNonNull(result, "result");
    if (!sessions.remove(session.getPlayerId(), session)) {
      return false;
    }
    return session.finish(result);
  }

  @Override
  public boolean complete(final DialogSession<?> session, final DialogResultType type) {
    if (!sessions.remove(session.getPlayerId(), session)) {
      return false;
    }
    return session.finishWithoutValue(Objects.requireNonNull(type, "type"));
  }

  @Override
  public boolean isActive(final DialogSession<?> session) {
    return sessions.get(session.getPlayerId()) == session;
  }

  @Override
  public boolean close(
      final ServiceOwner owner,
      final UUID playerId,
      final DialogResultType type
  ) {
    DialogSession<?> session = sessions.get(playerId);
    if (session == null || session.getOwner() != owner) {
      return false;
    }
    return complete(session, type);
  }

  @Override
  public void closeOwned(final ServiceOwner owner, final DialogResultType type) {
    sessions.values().stream()
        .filter(session -> session.getOwner() == owner)
        .toList()
        .forEach(session -> complete(session, type));
  }

  @Override
  public void closePlayer(final UUID playerId, final DialogResultType type) {
    DialogSession<?> session = sessions.get(playerId);
    if (session != null) {
      complete(session, type);
    }
  }

  @Override
  public void close() {
    sessions.values().stream().toList().forEach(
        session -> complete(session, DialogResultType.PLUGIN_DISABLED)
    );
  }
}
