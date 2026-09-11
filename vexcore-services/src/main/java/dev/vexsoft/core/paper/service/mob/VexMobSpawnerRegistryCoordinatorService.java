package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.mob.spawner.MobSpawnerDefinition;
import dev.vexsoft.core.paper.mob.spawner.MobSpawnerKey;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.bukkit.entity.Player;

/** Default global coordinator for custom mob spawner definitions. */
@Dependencies(MobSpawnerRuntimeCoordinatorService.class)
public final class VexMobSpawnerRegistryCoordinatorService implements MobSpawnerRegistryCoordinatorService {

    private final Map<MobSpawnerKey, Registration> definitions = new LinkedHashMap<>();
    private final MobSpawnerRuntimeCoordinatorService runtime;

    public VexMobSpawnerRegistryCoordinatorService(final VexServiceRegistry services) {
        runtime = services.require(MobSpawnerRuntimeCoordinatorService.class);
    }

    @Override
    public synchronized MobSpawnerDefinition register(final ServiceOwner owner, final MobSpawnerDefinition definition) {
        String ownerName = VexMobRegistryCoordinatorService.ownerName(owner);
        MobSpawnerDefinition checked = requireOwned(ownerName, definition);
        Registration existing = definitions.get(checked.key());

        if (existing != null && !existing.ownerName.equals(ownerName)) {
            throw new IllegalStateException("Mob spawner is owned by another plugin: " + checked.key());
        }

        if (existing != null && existing.definition.equals(checked)) {
            return existing.definition;
        }

        if (existing != null) {
            runtime.unregister(owner, checked.key(), true);
        }

        definitions.put(checked.key(), new Registration(ownerName, checked));
        runtime.register(owner, checked);

        return checked;
    }

    @Override
    public synchronized Collection<MobSpawnerDefinition> synchronize(
        final ServiceOwner owner,
        final Collection<MobSpawnerDefinition> desiredDefinitions
    ) {
        String ownerName = VexMobRegistryCoordinatorService.ownerName(owner);
        Map<MobSpawnerKey, MobSpawnerDefinition> desired = new LinkedHashMap<>();

        for (MobSpawnerDefinition definition : Objects.requireNonNull(desiredDefinitions, "definitions")) {
            MobSpawnerDefinition checked = requireOwned(ownerName, definition);

            if (desired.putIfAbsent(checked.key(), checked) != null) {
                throw new IllegalArgumentException("Duplicate mob spawner definition: " + checked.key());
            }
        }

        Set<MobSpawnerKey> removed = new LinkedHashSet<>();

        definitions.forEach((key, registration) -> {
            if (registration.ownerName.equals(ownerName) && !desired.containsKey(key)) {
                removed.add(key);
            }
        });
        removed.forEach(key -> unregister(owner, key));
        desired.values().forEach(definition -> register(owner, definition));

        return List.copyOf(desired.values());
    }

    @Override
    public synchronized Optional<MobSpawnerDefinition> find(final MobSpawnerKey key) {
        Registration registration = definitions.get(Objects.requireNonNull(key, "key"));

        return registration == null ? Optional.empty() : Optional.of(registration.definition);
    }

    @Override
    public synchronized boolean unregister(final ServiceOwner owner, final MobSpawnerKey key) {
        String ownerName = VexMobRegistryCoordinatorService.ownerName(owner);
        Registration registration = definitions.get(Objects.requireNonNull(key, "key"));

        if (registration == null) {
            return false;
        }

        if (!registration.ownerName.equals(ownerName)) {
            throw new IllegalArgumentException("Mob spawner is owned by another plugin: " + key);
        }

        runtime.unregister(owner, key, false);
        definitions.remove(key);

        return true;
    }

    @Override
    public synchronized void unregisterOwner(final ServiceOwner owner) {
        String ownerName = VexMobRegistryCoordinatorService.ownerName(owner);

        definitions.entrySet()
            .stream()
            .filter(entry -> entry.getValue().ownerName.equals(ownerName))
            .map(Map.Entry::getKey)
            .toList()
            .forEach(key -> unregister(owner, key));
    }

    @Override
    public synchronized Collection<MobSpawnerDefinition> getDefinitions() {
        return definitions.values().stream().map(registration -> registration.definition).toList();
    }

    @Override
    public void refresh(final Player player) {
        runtime.refresh(Objects.requireNonNull(player, "player"));
    }

    private static MobSpawnerDefinition requireOwned(final String ownerName, final MobSpawnerDefinition definition) {
        MobSpawnerDefinition checked = Objects.requireNonNull(definition, "definition");

        if (!checked.key().namespace().equals(ownerName)) {
            throw new IllegalArgumentException(
                "Mob spawner namespace must match its owner '" + ownerName + "': " + checked.key());
        }

        return checked;
    }

    private record Registration(String ownerName, MobSpawnerDefinition definition) {

    }
}
