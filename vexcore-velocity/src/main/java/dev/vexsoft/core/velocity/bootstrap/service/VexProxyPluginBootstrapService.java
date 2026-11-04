package dev.vexsoft.core.velocity.bootstrap.service;

import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.api.service.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.globaldata.GlobalDataService;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.cache.service.VexCacheService;
import dev.vexsoft.core.common.configuration.service.VexConfigurationService;
import dev.vexsoft.core.common.globaldata.service.VexGlobalDataService;
import dev.vexsoft.core.common.messaging.service.VexMessagingService;
import java.util.Objects;

/** Registers the standard owner-bound services for Velocity plugins. */
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
        checkedServices.register(GlobalDataService.class, VexGlobalDataService.class);
    }
}
