package dev.vexsoft.core.localization;

import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.localization.LocalizationService;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.Map;
import java.util.Objects;

@Dependencies({
    LocalizationRegistryService.class
})
public final class VexLocalizationService implements LocalizationService, AutoCloseable {

  private final LocalizationOwner owner;
  private final LocalizationRegistryService registry;

  public VexLocalizationService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
    if (!(services.getOwner() instanceof LocalizationOwner localizationOwner)) {
      throw new IllegalArgumentException("LocalizationService owner must support localizations");
    }
    owner = localizationOwner;
    registry = services.require(LocalizationRegistryService.class);
    registry.register(owner);
  }

  @Override
  public LocalizedMessage resolve(
      final LanguageKey language,
      final String key,
      final Map<String, String> replacements
  ) {
    return registry.resolve(owner, language, key, replacements);
  }

  @Override
  public void reload() {
    registry.reload(owner);
  }

  @Override
  public void close() {
    registry.unregister(owner);
  }
}
