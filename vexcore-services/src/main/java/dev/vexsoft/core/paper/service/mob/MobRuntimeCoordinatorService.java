package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.mob.MobDamageResult;
import dev.vexsoft.core.paper.mob.MobDefinition;
import dev.vexsoft.core.paper.mob.MobHandle;
import dev.vexsoft.core.paper.mob.MobKey;
import dev.vexsoft.core.paper.mob.MobRemovalReason;
import dev.vexsoft.core.paper.mob.MobSnapshot;
import dev.vexsoft.core.paper.mob.MobSpawnRequest;
import dev.vexsoft.core.paper.packets.display.DisplayGlowColor;
import java.util.Collection;
import java.util.Optional;
import org.bukkit.entity.Entity;

/** Coordinates all live, non-persistent custom mob instances. */
public interface MobRuntimeCoordinatorService extends VexService {

  MobHandle spawn(ServiceOwner owner, MobDefinition definition, MobSpawnRequest request);

  Optional<MobSnapshot> find(ServiceOwner owner, MobHandle handle);

  Optional<MobSnapshot> find(Entity entity);

  Collection<MobSnapshot> getActiveMobs(ServiceOwner owner);

  MobDamageResult damage(ServiceOwner owner, MobHandle handle, double amount);

  MobSnapshot setHealth(ServiceOwner owner, MobHandle handle, double health);

  MobSnapshot setScale(ServiceOwner owner, MobHandle handle, double scale);

  MobSnapshot setGlow(ServiceOwner owner, MobHandle handle, DisplayGlowColor color);

  MobSnapshot clearGlow(ServiceOwner owner, MobHandle handle);

  void refreshPresentation(ServiceOwner owner, MobHandle handle);

  boolean remove(ServiceOwner owner, MobHandle handle, MobRemovalReason reason);

  void removeSafely(ServiceOwner owner, MobHandle handle, MobRemovalReason reason);

  int removeAll(ServiceOwner owner, MobRemovalReason reason);

  int removeDefinition(ServiceOwner owner, MobKey key);

  void addRemovalListener(MobRuntimeRemovalListener listener);

  void removeRemovalListener(MobRuntimeRemovalListener listener);

  void start();

  void shutdown();
}
