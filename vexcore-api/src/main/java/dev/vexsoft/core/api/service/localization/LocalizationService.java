package dev.vexsoft.core.api.service.localization;

import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizedMessage;

import dev.vexsoft.core.api.service.registry.VexService;
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
