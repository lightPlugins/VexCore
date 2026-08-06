package dev.vexsoft.core.api.localization;

import dev.vexsoft.core.api.service.VexService;
import java.util.Map;

/**
 * Resolves localized messages owned by the current plugin
 */
public interface LocalizationService extends VexService {

  /** Resolves a localized message for an explicitly selected language */
  LocalizedMessage resolve(LanguageKey language, String key, Map<String, String> replacements);

  /** Reloads every language file owned by this service */
  void reload();
}
