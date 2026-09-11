package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.mob.MobDefinition;
import dev.vexsoft.core.paper.mob.MobKey;
import java.util.Collection;
import java.util.Optional;

/** Coordinates custom mob definitions across owner-scoped registry facades. */
public interface MobRegistryCoordinatorService extends VexService {

    /** Registers or updates a mob definition belonging to the supplied owner. */
    MobDefinition register(ServiceOwner owner, MobDefinition definition);

    /** Reconciles the owner's registered mob definitions with the supplied collection. */
    Collection<MobDefinition> synchronize(ServiceOwner owner, Collection<MobDefinition> definitions);

    /** Finds a registered mob definition by its stable key. */
    Optional<MobDefinition> find(MobKey key);

    /** Removes a mob definition belonging to the supplied owner. */
    boolean unregister(ServiceOwner owner, MobKey key);

    /** Removes every mob definition belonging to the supplied owner. */
    void unregisterOwner(ServiceOwner owner);

    /** Returns a snapshot of all registered mob definitions. */
    Collection<MobDefinition> getDefinitions();

    /** Checks whether the owner registered the given mob definition. */
    boolean owns(ServiceOwner owner, MobKey key);
}
