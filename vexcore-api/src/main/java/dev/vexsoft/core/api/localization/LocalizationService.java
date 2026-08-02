package dev.vexsoft.core.api.localization;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.VexService;
import java.util.Map;

public interface LocalizationService extends VexService {

  /** Resolves a localized message for the current language of a Vex player */
  public LocalizedMessage resolve(VexPlayer player, String key);

  /** Resolves a localized message with string replacements for a Vex player */
  public LocalizedMessage resolve(VexPlayer player, String key, Map<String, String> replacements);

  /** Resolves a localized message for an explicitly selected language */
  public LocalizedMessage resolve(LanguageKey language, String key, Map<String, String> replacements);

  /** Reloads every language file owned by this service */
  public void reload();
}
