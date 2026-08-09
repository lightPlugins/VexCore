package dev.vexsoft.core.paper.service.dialogs;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.dialogs.DialogResult;
import dev.vexsoft.core.paper.dialogs.DialogResultType;
import java.util.UUID;

/**
 * Coordinates active dialogs across every Vex plugin
 */
public interface DialogCoordinatorService extends VexService {

  /** Starts a session and replaces any dialog already tracked for the player */
  <T> DialogSession<T> begin(ServiceOwner owner, UUID playerId);

  /** Completes the session when it is still the player's active dialog */
  <T> boolean complete(DialogSession<T> session, DialogResult<T> result);

  /** Completes the session without a value when it is still active */
  boolean complete(DialogSession<?> session, DialogResultType type);

  /** Returns whether the session is still active */
  boolean isActive(DialogSession<?> session);

  /** Closes the active session for the player when it belongs to the owner */
  boolean close(ServiceOwner owner, UUID playerId, DialogResultType type);

  /** Closes every active session belonging to the owner */
  void closeOwned(ServiceOwner owner, DialogResultType type);

  /** Closes the active session for a player regardless of its owner */
  void closePlayer(UUID playerId, DialogResultType type);
}
