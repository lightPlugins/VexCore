package dev.vexsoft.core.paper.service.reactor;

import dev.vexsoft.core.paper.reactor.provider.BlockTypeProvider;

import dev.vexsoft.core.api.service.registry.VexService;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.block.Block;

/** Registers namespaced block providers and compiles block-key matchers. */
public interface BlockTypeRegistry extends VexService {

  /** Registers one provider class owned by this service scope. */
  void register(Class<? extends BlockTypeProvider> providerType);

  /** Compiles a fully qualified block key with its registered namespace provider. */
  Predicate<Block> compile(Key key);
}
