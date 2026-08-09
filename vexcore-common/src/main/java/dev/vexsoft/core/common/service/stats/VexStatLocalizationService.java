package dev.vexsoft.core.common.service.stats;

import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.placeholder.PlaceholderService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.stats.StatLocalizationService;
import dev.vexsoft.core.common.service.localization.LocalizationRegistryService;
import dev.vexsoft.core.stats.Stat;
import dev.vexsoft.core.stats.StatDefinition;
import dev.vexsoft.core.stats.StatKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Owner-aware stat localization backed by the shared localization registry. */
@Dependencies({
    StatRegistryCoordinatorService.class,
    LocalizationRegistryService.class,
    PlaceholderService.class
})
public final class VexStatLocalizationService implements StatLocalizationService {

  private final StatRegistryCoordinatorService stats;
  private final LocalizationRegistryService localizations;
  private final PlaceholderService placeholders;

  public VexStatLocalizationService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    stats = checkedServices.require(StatRegistryCoordinatorService.class);
    localizations = checkedServices.require(LocalizationRegistryService.class);
    placeholders = checkedServices.require(PlaceholderService.class);
  }

  @Override
  public Component getName(final VexPlayer player, final StatKey stat) {
    VexPlayer checkedPlayer = Objects.requireNonNull(player, "player");
    LocalizedMessage message = resolve(checkedPlayer, stat, true);
    return placeholders.resolve(checkedPlayer, message.getLines().getFirst());
  }

  @Override
  public List<Component> getDescription(final VexPlayer player, final StatKey stat) {
    VexPlayer checkedPlayer = Objects.requireNonNull(player, "player");
    return resolve(checkedPlayer, stat, false).getLines().stream()
        .map(line -> placeholders.resolve(checkedPlayer, line))
        .toList();
  }

  private LocalizedMessage resolve(
      final VexPlayer player,
      final StatKey key,
      final boolean name
  ) {
    Stat stat = stats.find(Objects.requireNonNull(key, "stat")).orElseThrow(
        () -> new IllegalStateException("Stat is not registered: " + key)
    );
    StatDefinition definition = stat.getDefinition();
    String localizationKey = name ? definition.getNameKey() : definition.getDescriptionKey();
    return localizations.resolve(
        key.namespace(),
        player.getContainer(LanguageContainer.class).getLanguage().getKey(),
        localizationKey,
        Map.of()
    );
  }
}
