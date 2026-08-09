package dev.vexsoft.core.paper.service.reactor;

import dev.vexsoft.core.paper.reactor.provider.EntityTypeProvider;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Entity;

public interface EntityTypeCoordinatorService extends VexService {
  void register(ServiceOwner owner, VexServiceRegistry services,
      Class<? extends EntityTypeProvider> providerType);
  Predicate<Entity> compile(Key key);
  void unregisterOwner(ServiceOwner owner);
}
