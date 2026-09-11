package dev.vexsoft.core.paper.mob.event;

import dev.vexsoft.core.paper.mob.MobRemovalReason;
import dev.vexsoft.core.paper.mob.MobSnapshot;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Exactly-once runtime removal notification, including cleanup and non-combat removal. */
public final class MobRemovedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final MobSnapshot mob;
    private final MobRemovalReason reason;
    private final Entity attacker;

    /** Captures the final snapshot and explicit lifecycle reason. */
    public MobRemovedEvent(final MobSnapshot mob, final MobRemovalReason reason) {
        this(mob, reason, null);
    }

    /** Captures the causing entity of a lethal custom hit, otherwise null. */
    public MobRemovedEvent(final MobSnapshot mob, final MobRemovalReason reason, final Entity attacker) {
        this.mob = Objects.requireNonNull(mob, "mob");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.attacker = attacker;
    }

    /** Returns the attacker responsible for a lethal hit, excluding administrative removal. */
    public Optional<Entity> getAttacker() {
        return Optional.ofNullable(attacker);
    }

    /** Returns the last snapshot captured for the removed mob. */
    public MobSnapshot getMob() {
        return mob;
    }

    /** Returns why the runtime removed the mob. */
    public MobRemovalReason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** Returns Bukkit's event handler registry. */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
