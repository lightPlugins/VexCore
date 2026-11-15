package dev.vexsoft.core.api.network;

import java.util.Optional;
import net.kyori.adventure.text.Component;

/** Signals that the failed backend has saved a replacement ticket and has no live state to transfer. */
public final class ArrivalRecovery {

    private static final String MARKER = "vexcore:arrival_recovery:v1:";

    private ArrivalRecovery() {
    }

    public static Component reason(final ServerId target) {
        return Component.translatable("multiplayer.disconnect.server_shutdown").insertion(MARKER + target.value());
    }

    public static Optional<ServerId> target(final Component reason) {
        String value = reason.insertion();
        if (value == null || !value.startsWith(MARKER)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ServerId(value.substring(MARKER.length())));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
