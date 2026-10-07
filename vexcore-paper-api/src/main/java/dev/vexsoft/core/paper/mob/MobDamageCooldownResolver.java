package dev.vexsoft.core.paper.mob;

import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/** Resolves entity-hit timing from the direct source and its damage cause. */
@FunctionalInterface
public interface MobDamageCooldownResolver {

    /** Returns a nonnegative cooldown in ticks; zero delegates timing to the damage source. */
    int resolve(Entity directSource, DamageCause cause);
}
