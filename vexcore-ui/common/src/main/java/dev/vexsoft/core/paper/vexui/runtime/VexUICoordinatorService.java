package dev.vexsoft.core.paper.vexui.runtime;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.vexui.VexUISession;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;

/** Coordinates viewer ownership across all owner-scoped VexUI service instances. */
public interface VexUICoordinatorService extends VexService {

    /** Installs a session and retires the old owner without closing the replacement screen. */
    void replace(VexUISessionState session);

    /** Returns whether the session is still the viewer's current session. */
    boolean active(VexUISessionState session);

    /** Releases only the specified session, leaving replacements intact. */
    void release(VexUISessionState session);

    /** Returns whether any owner currently has a session for this viewer. */
    boolean occupied(UUID viewer);

    /** Finds an active session belonging to the specified owner. */
    Optional<VexUISession> find(ServiceOwner owner, UUID viewer);

    /** Dispatches an opaque client action to the viewer's current session. */
    void dispatch(Player player, Key action, String submittedText);

    /** Retires a disconnected viewer's session without sending packets. */
    void disconnected(UUID viewer);

    /** Closes every session owned by a service scope. */
    void closeOwned(ServiceOwner owner);

    /** Refreshes active sessions affected by an owner's presentation changes. */
    void refreshOwned(ServiceOwner owner);
}
