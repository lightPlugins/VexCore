package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.mob.MobDamageResult;
import dev.vexsoft.core.paper.mob.MobDefinition;
import dev.vexsoft.core.paper.mob.MobHandle;
import dev.vexsoft.core.paper.mob.MobRemovalReason;
import dev.vexsoft.core.paper.mob.MobSnapshot;
import dev.vexsoft.core.paper.mob.MobSpawnRequest;
import dev.vexsoft.core.paper.packets.display.DisplayGlowColor;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.Entity;

/** Default owner-scoped custom mob lifecycle facade. */
@Dependencies({MobRegistryCoordinatorService.class, MobRuntimeCoordinatorService.class})
public final class VexMobService implements MobService, AutoCloseable {

  private final ServiceOwner owner;
  private final MobRegistryCoordinatorService definitions;
  private final MobRuntimeCoordinatorService runtime;

  public VexMobService(final VexServiceRegistry services) {
    VexServiceRegistry checked = Objects.requireNonNull(services, "services");
    owner = checked.getOwner();
    definitions = checked.require(MobRegistryCoordinatorService.class);
    runtime = checked.require(MobRuntimeCoordinatorService.class);
  }

  @Override
  public MobHandle spawn(final MobSpawnRequest request) {
    MobSpawnRequest checked = Objects.requireNonNull(request, "request");
    if (!definitions.owns(owner, checked.definitionKey())) {
      throw new IllegalArgumentException(
          "Mob definition is not owned by this plugin: " + checked.definitionKey()
      );
    }
    MobDefinition definition = definitions.find(checked.definitionKey()).orElseThrow();
    return runtime.spawn(owner, definition, checked);
  }

  @Override
  public Optional<MobSnapshot> find(final MobHandle handle) {
    return runtime.find(owner, handle);
  }

  @Override
  public Optional<MobSnapshot> find(final Entity entity) {
    return runtime.find(entity);
  }

  @Override
  public Collection<MobSnapshot> getActiveMobs() {
    return runtime.getActiveMobs(owner);
  }

  @Override
  public MobDamageResult damage(final MobHandle handle, final double amount) {
    return runtime.damage(owner, handle, amount);
  }

  @Override
  public MobSnapshot setHealth(final MobHandle handle, final double health) {
    return runtime.setHealth(owner, handle, health);
  }

  @Override
  public MobSnapshot setScale(final MobHandle handle, final double scale) {
    return runtime.setScale(owner, handle, scale);
  }

  @Override
  public MobSnapshot setGlow(final MobHandle handle, final DisplayGlowColor color) {
    return runtime.setGlow(owner, handle, color);
  }

  @Override
  public MobSnapshot clearGlow(final MobHandle handle) {
    return runtime.clearGlow(owner, handle);
  }

  @Override
  public void refreshPresentation(final MobHandle handle) {
    runtime.refreshPresentation(owner, handle);
  }

  @Override
  public boolean remove(final MobHandle handle, final MobRemovalReason reason) {
    return runtime.remove(owner, handle, reason);
  }

  @Override
  public int removeAll(final MobRemovalReason reason) {
    return runtime.removeAll(owner, reason);
  }

  @Override
  public void close() {
    runtime.removeAll(owner, MobRemovalReason.PLUGIN_DISABLE);
  }
}
