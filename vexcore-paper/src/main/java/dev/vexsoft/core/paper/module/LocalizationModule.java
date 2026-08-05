package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.localization.LanguageService;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.localization.LocalizationRegistryService;
import dev.vexsoft.core.localization.VexLanguageService;
import dev.vexsoft.core.localization.VexLocalizationRegistryService;
import dev.vexsoft.core.localization.LanguageChangeDispatcherService;
import dev.vexsoft.core.paper.localization.VexLanguageChangeDispatcherService;

public final class LocalizationModule implements VexModule {

  private VexServiceRegistry services;

  @Override
  public void enable(final VexServiceRegistry registry) {
    services = registry.scoped(this);
    services.register(LanguageChangeDispatcherService.class, VexLanguageChangeDispatcherService.class);
    services.register(LocalizationRegistryService.class, VexLocalizationRegistryService.class);
    services.register(LanguageService.class, VexLanguageService.class);
    services.registerQueuedServices();
  }

  @Override
  public void disable() {
    if (services != null) {
      services.unregisterOwnedServices();
    }
  }

  @Override
  public String getServiceOwnerName() {
    return "vexcore_localization";
  }
}
