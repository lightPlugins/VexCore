package dev.vexsoft.core.paper.dialog;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.dialog.DialogResult;
import dev.vexsoft.core.dialog.DialogResultType;
import java.util.UUID;

/**
 * Coordinates active dialogs across every Vex plugin
 */
public interface DialogCoordinatorService extends VexService {

  /** Starts a session and replaces any dialog already tracked for the player */
  public <T> DialogSession<T> begin(ServiceOwner owner, UUID playerId);

  /** Completes the session when it is still the player's active dialog */
  public <T> boolean complete(DialogSession<T> session, DialogResult<T> result);

  /** Completes the session without a value when it is still active */
  public boolean complete(DialogSession<?> session, DialogResultType type);

  /** Returns whether the session is still active */
  public boolean isActive(DialogSession<?> session);

  /** Closes the active session for the player when it belongs to the owner */
  public boolean close(ServiceOwner owner, UUID playerId, DialogResultType type);

  /** Closes every active session belonging to the owner */
  public void closeOwned(ServiceOwner owner, DialogResultType type);

  /** Closes the active session for a player regardless of its owner */
  public void closePlayer(UUID playerId, DialogResultType type);
}
