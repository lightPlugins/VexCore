package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import org.bukkit.entity.Player;

/** Synchronizes the closed state of vanilla recipe-book panels. */
public interface RecipeBookPacketAdapterService extends VexService {

    /** Closes every recipe-book panel while retaining recipe discoveries and filters. */
    void closeRecipeBook(Player player);
}
