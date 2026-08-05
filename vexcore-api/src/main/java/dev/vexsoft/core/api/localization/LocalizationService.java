package dev.vexsoft.core.api.localization;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.VexService;
import java.util.Map;

/**
 * Resolves localized messages owned by the current plugin
 */
public interface LocalizationService extends VexService {

  /** Resolves a localized message for the current language of a Vex player */
  LocalizedMessage resolve(VexPlayer player, String key);

  /** Resolves a localized message with string replacements for a Vex player */
  LocalizedMessage resolve(VexPlayer player, String key, Map<String, String> replacements);

  /** Resolves a localized message for an explicitly selected language */
  LocalizedMessage resolve(LanguageKey language, String key, Map<String, String> replacements);

  /** Reloads every language file owned by this service */
  void reload();
}
