package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.mob.MobDefinition;
import dev.vexsoft.core.paper.mob.MobKey;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/** Default owner-scoped custom mob registry. */
@Dependencies(MobRegistryCoordinatorService.class)
public final class VexMobRegistry implements MobRegistry, AutoCloseable {

    private final ServiceOwner owner;
    private final MobRegistryCoordinatorService coordinator;

    public VexMobRegistry(final VexServiceRegistry services) {
        VexServiceRegistry checked = Objects.requireNonNull(services, "services");

        owner = checked.getOwner();
        coordinator = checked.require(MobRegistryCoordinatorService.class);
    }

    @Override
    public MobDefinition register(final MobDefinition definition) {
        return coordinator.register(owner, definition);
    }

    @Override
    public Collection<MobDefinition> synchronize(final Collection<MobDefinition> definitions) {
        return coordinator.synchronize(owner, definitions);
    }

    @Override
    public Optional<MobDefinition> find(final MobKey key) {
        return coordinator.find(key);
    }

    @Override
    public boolean unregister(final MobKey key) {
        return coordinator.unregister(owner, key);
    }

    @Override
    public Collection<MobDefinition> getDefinitions() {
        return coordinator.getDefinitions();
    }

    @Override
    public void close() {
        coordinator.unregisterOwner(owner);
    }
}
