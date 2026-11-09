package dev.vexsoft.core.paper.nms.service;

import dev.vexsoft.core.api.mob.flight.FlightBounds;
import dev.vexsoft.core.api.mob.flight.FlightObstacles;
import dev.vexsoft.core.api.mob.flight.FlightProfile;
import dev.vexsoft.core.api.mob.flight.FlightState;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.nms.goal.NmsFollowOwnerSpec;
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

    /** Configures native flight speed handling and optional terrain clearance; ground movement is unchanged. */
    default void configureFlight(Mob mob, double minimumHeight) {
        configureFlight(mob, minimumHeight, FlightProfile.defaults());
    }

    /** Installs the authoritative controller on supported flying carriers. */
    void configureFlight(Mob mob, double minimumHeight, FlightProfile profile);

    /** Replaces the explicit destination in blocks/second, retaining motion between orders. */
    default FlightState flyTo(Mob mob, Location destination, double speed) {
        return flyTo(mob, destination, speed, null);
    }

    /** Replaces an order and its optional movement volume. */
    FlightState flyTo(Mob mob, Location destination, double speed, FlightBounds bounds);

    /** Applies an explicit order's terrain exclusions to the authoritative flight motor. */
    default FlightState flyTo(Mob mob, Location destination, double speed, FlightBounds bounds,
        FlightObstacles obstacles) {
        if (obstacles != FlightObstacles.SOLID) {
            throw new UnsupportedOperationException("Selective flight collisions are unavailable");
        }
        return flyTo(mob, destination, speed, bounds);
    }

    /** Releases an explicit order without resetting momentum or teleporting the carrier. */
    void releaseFlight(Mob mob);

    /** Installs one native random movement goal. */
    void addRandomMovement(Mob mob, NmsRandomMovementSpec spec);

    /** Installs one native scope-aware look-at-player goal. */
    void addLookAtPlayer(Mob mob, NmsLookAtPlayerSpec spec);

    /** Installs owner-scoped pursuit and melee combat. */
    void addOwnerMelee(Mob mob, NmsOwnerMeleeSpec spec);

    /** Installs passive navigation toward a fixed owner, independently of viewer scope. */
    void addFollowOwner(Mob mob, NmsFollowOwnerSpec spec);

    /** Activates native goal processing after every requested goal has been installed. */
    void activateGoals(Mob mob);

    /** Stops navigation, removes every custom goal, and disables native AI processing. */
    void deactivateGoals(Mob mob);
}
