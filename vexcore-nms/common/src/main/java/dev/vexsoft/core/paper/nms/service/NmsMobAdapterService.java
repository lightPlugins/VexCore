package dev.vexsoft.core.paper.nms.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.nms.goal.NmsLookAtPlayerSpec;
import dev.vexsoft.core.paper.nms.goal.NmsOwnerMeleeSpec;
import dev.vexsoft.core.paper.nms.goal.NmsRandomMovementSpec;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Mob;

/** Native entity control used behind VexCore's version-neutral mob runtime. */
public interface NmsMobAdapterService extends VexService {

    /** Encodes spawn, metadata and attributes for an unspawned visual carrier. */
    List<Object> visualSpawnPackets(Mob mob);

    /** Encodes an interpolated client movement without moving or ticking a server entity. */
    Object visualMovePacket(Mob mob, Location location);

    /** Encodes removal of the unspawned visual carrier. */
    Object visualRemovePacket(Mob mob);

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
