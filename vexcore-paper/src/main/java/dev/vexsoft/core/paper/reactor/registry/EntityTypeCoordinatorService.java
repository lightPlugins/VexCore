package dev.vexsoft.core.paper.reactor.registry;

import dev.vexsoft.core.paper.reactor.provider.EntityTypeProvider;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Entity;

public interface EntityTypeCoordinatorService extends VexService {
  void register(ServiceOwner owner, VexServiceRegistry services,
      Class<? extends EntityTypeProvider> providerType);
  Predicate<Entity> compile(Key key);
  void unregisterOwner(ServiceOwner owner);
}
