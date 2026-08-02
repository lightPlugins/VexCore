package dev.vexsoft.core.paper.localization;

import dev.vexsoft.core.api.localization.Language;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.localization.LanguageChangeDispatcherService;
import java.util.Objects;
import org.bukkit.Bukkit;

@Dependencies
public final class VexLanguageChangeDispatcherService implements LanguageChangeDispatcherService {

  public VexLanguageChangeDispatcherService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public void dispatch(
      final VexPlayer player,
      final Language previousLanguage,
      final Language newLanguage
  ) {
    Bukkit.getPluginManager().callEvent(new VexPlayerLanguageChangeEvent(
        player,
        previousLanguage,
        newLanguage,
        !Bukkit.isPrimaryThread()
    ));
  }
}
