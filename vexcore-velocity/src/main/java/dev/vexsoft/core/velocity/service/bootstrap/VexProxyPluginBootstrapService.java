package dev.vexsoft.core.velocity.service.bootstrap;

import dev.vexsoft.core.api.service.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.common.service.cache.VexCacheService;
import dev.vexsoft.core.common.service.configuration.VexConfigurationService;
import dev.vexsoft.core.common.service.messaging.VexMessagingService;
import java.util.Objects;

@Dependencies
public final class VexProxyPluginBootstrapService implements ProxyPluginBootstrapService {

  public VexProxyPluginBootstrapService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public void initialize(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    checkedServices.register(ConfigurationService.class, VexConfigurationService.class);
    checkedServices.register(CacheService.class, VexCacheService.class);
    checkedServices.register(MessagingService.class, VexMessagingService.class);
  }
}
