package dev.vexsoft.core.common.service.localization;

import dev.vexsoft.core.api.localization.Language;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;

/**
 * Publishes player language changes to the active server platform
 */
public interface LanguageChangeDispatcherService extends VexService {

  /** Publishes a completed language change to the current platform */
  void dispatch(VexPlayer player, Language previousLanguage, Language newLanguage);
}
