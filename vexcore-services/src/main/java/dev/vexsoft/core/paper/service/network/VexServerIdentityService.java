package dev.vexsoft.core.paper.service.network;

import dev.vexsoft.core.api.configuration.VexConfiguration;
import dev.vexsoft.core.api.network.ServerId;
import dev.vexsoft.core.api.service.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.nio.file.Path;
import java.util.Objects;

/** Loads the current backend ID configured for Velocity routing. */
@Dependencies(ConfigurationService.class)
public final class VexServerIdentityService implements ServerIdentityService {

  private final ServerId serverId;

  public VexServerIdentityService(final VexServiceRegistry services) {
    VexConfiguration configuration = Objects.requireNonNull(services, "services")
        .require(ConfigurationService.class)
        .load(Path.of("network.yml"), "network.yml");
    serverId = new ServerId(configuration.getString("server-id", "server"));
  }

  @Override
  public ServerId getServerId() {
    return serverId;
  }
}
