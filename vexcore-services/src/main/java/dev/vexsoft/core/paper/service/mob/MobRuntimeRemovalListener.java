package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.paper.mob.MobHandle;
import dev.vexsoft.core.paper.mob.MobRemovalReason;

/** Receives idempotent internal notifications after a runtime mob leaves the registry. */
@FunctionalInterface
public interface MobRuntimeRemovalListener {

  void onRemoved(MobHandle handle, MobRemovalReason reason);
}
