package dev.vexsoft.core.api.network;

import net.kyori.adventure.text.Component;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Identifies a controlled backend shutdown after its player persistence has finished. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BackendShutdown {

    private static final String MARKER = "vexcore:backend_shutdown:v1";

    /** Returns the vanilla localized shutdown reason with metadata understood by the Core proxy. */
    public static Component reason() {
        return Component.translatable("multiplayer.disconnect.server_shutdown").insertion(MARKER);
    }

    /** Checks the metadata without depending on the player's language or matching arbitrary kick text. */
    public static boolean matches(final Component reason) {
        return reason != null && MARKER.equals(reason.insertion());
    }
}
