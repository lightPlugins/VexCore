package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.localization.LanguageService;
import dev.vexsoft.core.api.service.player.PlayerContainerService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.localization.LocalizationRegistryService;
import dev.vexsoft.core.common.service.localization.VexLanguageService;
import dev.vexsoft.core.common.service.localization.VexLocalizationRegistryService;
import dev.vexsoft.core.common.service.localization.LanguageChangeDispatcherService;
import dev.vexsoft.core.common.service.data.VexPlayerContainerService;
import dev.vexsoft.core.paper.service.localization.VexLanguageChangeDispatcherService;
import dev.vexsoft.core.common.service.localization.editor.LocalizationEditorService;
import dev.vexsoft.core.common.service.localization.editor.VexLocalizationEditorService;

public final class LocalizationModule implements VexModule {

  private VexServiceRegistry services;

  @Override
  public void enable(final VexServiceRegistry registry) {
    services = registry.scoped(this);
    services.register(PlayerContainerService.class, VexPlayerContainerService.class);
    services.register(LanguageChangeDispatcherService.class, VexLanguageChangeDispatcherService.class);
    services.register(LocalizationRegistryService.class, VexLocalizationRegistryService.class);
    services.register(LocalizationEditorService.class, VexLocalizationEditorService.class);
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
