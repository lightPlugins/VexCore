package dev.vexsoft.core.velocity.bootstrap;

import dev.vexsoft.core.api.configuration.ConfigurationService;
import dev.vexsoft.core.api.messaging.MessagingService;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.cache.CacheService;
import dev.vexsoft.core.cache.VexCacheService;
import dev.vexsoft.core.configuration.VexConfigurationService;
import dev.vexsoft.core.messaging.VexMessagingService;
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
