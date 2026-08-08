package dev.vexsoft.core.paper.reactor.registry;

import dev.vexsoft.core.paper.reactor.provider.ItemTypeProvider;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;

public interface ItemTypeCoordinatorService extends VexService {
  void register(ServiceOwner owner, VexServiceRegistry services,
      Class<? extends ItemTypeProvider> providerType);
  Predicate<ItemStack> compile(Key key);
  void unregisterOwner(ServiceOwner owner);
}
