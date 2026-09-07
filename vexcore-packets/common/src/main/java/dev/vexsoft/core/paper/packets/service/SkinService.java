package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.dummy.SkinTexture;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;

/** Resolves player skin properties asynchronously using bounded shared caches. */
public interface SkinService extends VexService {
  /** Call on the player's owning thread; never blocks for network access. */
  CompletableFuture<Optional<SkinTexture>> resolve(Player player);
}
