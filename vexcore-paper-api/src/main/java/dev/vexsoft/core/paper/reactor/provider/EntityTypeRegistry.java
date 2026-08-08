package dev.vexsoft.core.paper.reactor.provider;

import dev.vexsoft.core.api.service.VexService;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Entity;

/** Registers namespaced entity providers and compiles entity-key matchers. */
public interface EntityTypeRegistry extends VexService {

  /** Registers one provider class owned by this service scope. */
  void register(Class<? extends EntityTypeProvider> providerType);

  /** Compiles a fully qualified entity key with its registered namespace provider. */
  Predicate<Entity> compile(Key key);
}
