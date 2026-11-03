package dev.vexsoft.core.paper.service.effect;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.effect.ItemCollectionRequest;
import org.bukkit.entity.Player;

/** Shared, bounded viewer-only collection flights with no economic side effects. */
public interface ItemCollectionEffectService extends VexService {

    /** Cheap entity-thread hint to avoid building display items when capacity is exhausted; does not reserve a slot. */
    boolean hasCapacity(Player player);

    /** Enqueues one committed block on the entity thread; plays an arrival chime, leaving reward pickup audio separate. */
    void play(Player player, ItemCollectionRequest request);
}
