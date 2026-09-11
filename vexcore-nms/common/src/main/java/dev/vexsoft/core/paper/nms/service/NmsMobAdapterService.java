package dev.vexsoft.core.paper.nms.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.nms.goal.NmsLookAtPlayerSpec;
import dev.vexsoft.core.paper.nms.goal.NmsOwnerMeleeSpec;
import dev.vexsoft.core.paper.nms.goal.NmsRandomMovementSpec;
import org.bukkit.entity.Mob;

/** Native entity control used behind VexCore's version-neutral mob runtime. */
public interface NmsMobAdapterService extends VexService {

    /** Removes vanilla behavior and leaves the mob in a passive non-persistent state. */
    void neutralize(Mob mob);

    /** Installs one native random movement goal. */
    void addRandomMovement(Mob mob, NmsRandomMovementSpec spec);

    /** Installs one native scope-aware look-at-player goal. */
    void addLookAtPlayer(Mob mob, NmsLookAtPlayerSpec spec);

    /** Installs owner-scoped pursuit and melee combat. */
    void addOwnerMelee(Mob mob, NmsOwnerMeleeSpec spec);

    /** Activates native goal processing after every requested goal has been installed. */
    void activateGoals(Mob mob);

    /** Stops navigation, removes every custom goal, and disables native AI processing. */
    void deactivateGoals(Mob mob);
}
