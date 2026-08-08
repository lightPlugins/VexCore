package dev.vexsoft.core.paper.reactor.registry;

import dev.vexsoft.core.paper.reactor.provider.BlockTypeProvider;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.block.Block;

public interface BlockTypeCoordinatorService extends VexService {

  void register(
      ServiceOwner owner,
      VexServiceRegistry services,
      Class<? extends BlockTypeProvider> providerType
  );

  Predicate<Block> compile(Key key);

  void unregisterOwner(ServiceOwner owner);
}
