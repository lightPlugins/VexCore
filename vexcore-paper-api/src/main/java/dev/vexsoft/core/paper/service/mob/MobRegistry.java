package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.mob.MobDefinition;
import dev.vexsoft.core.paper.mob.MobKey;
import java.util.Collection;
import java.util.Optional;

/** Owner-scoped registry of immutable custom mob definitions. */
public interface MobRegistry extends VexService {

    /** Registers or replaces an owned definition. */
    MobDefinition register(MobDefinition definition);

    /** Reconciles every definition owned by this registry scope. */
    Collection<MobDefinition> synchronize(Collection<MobDefinition> definitions);

    /** Finds an active definition regardless of owner. */
    Optional<MobDefinition> find(MobKey key);

    /** Removes one owned definition and its active runtime mobs. */
    boolean unregister(MobKey key);

    /** Returns every currently registered definition. */
    Collection<MobDefinition> getDefinitions();
}
