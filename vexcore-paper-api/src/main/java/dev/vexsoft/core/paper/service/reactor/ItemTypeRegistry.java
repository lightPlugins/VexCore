package dev.vexsoft.core.paper.service.reactor;

import dev.vexsoft.core.paper.reactor.provider.ItemTypeProvider;

import dev.vexsoft.core.api.service.registry.VexService;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;

/** Registers namespaced item providers and compiles item-key matchers. */
public interface ItemTypeRegistry extends VexService {

  /** Registers one provider class owned by this service scope. */
  void register(Class<? extends ItemTypeProvider> providerType);

  /** Compiles a fully qualified item key with its registered namespace provider. */
  Predicate<ItemStack> compile(Key key);
}
