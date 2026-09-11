package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.dummy.SkinTexture;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;

/** Provides asynchronous access to player skin textures. */
public interface SkinService extends VexService {

    /**
     * Returns the player's skin, or an empty result if unavailable.
     * Must be called on the player's owning thread; network requests run asynchronously.
     */
    CompletableFuture<Optional<SkinTexture>> resolve(Player player);
}
