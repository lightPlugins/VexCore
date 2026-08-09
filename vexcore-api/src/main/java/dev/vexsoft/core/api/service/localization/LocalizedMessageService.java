package dev.vexsoft.core.api.service.localization;

import dev.vexsoft.core.api.localization.LanguageKey;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.Map;
import net.kyori.adventure.audience.Audience;

/** Sends localized messages owned by the current plugin without platform-specific player types. */
public interface LocalizedMessageService extends VexService {

  /** Sends a localized message to a loaded Vex player without a prefix. */
  default void send(final VexPlayer player, final String key) {
    send(player, key, false, Map.of());
  }

  /** Sends a localized message to a loaded Vex player with optional prefix handling. */
  default void send(final VexPlayer player, final String key, final boolean withPrefix) {
    send(player, key, withPrefix, Map.of());
  }

  /** Sends a localized message with replacements to a loaded Vex player. */
  default void send(
      final VexPlayer player,
      final String key,
      final Map<String, String> replacements
  ) {
    send(player, key, false, replacements);
  }

  /** Sends a localized message using the language selected by a loaded Vex player. */
  void send(
      VexPlayer player,
      String key,
      boolean withPrefix,
      Map<String, String> replacements
  );

  /** Sends a localized message to an audience using an explicitly selected language. */
  void send(
      Audience audience,
      LanguageKey language,
      String key,
      boolean withPrefix,
      Map<String, String> replacements
  );
}
