package dev.vexsoft.core.paper.mob.event;

import dev.vexsoft.core.paper.mob.MobSnapshot;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Mutable custom-health damage boundary, dispatched before a mob is changed or removed. */
public final class MobDamageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final MobSnapshot mob;
    private final Entity attacker;
    private final double attackCharge;
    private double damage;
    private boolean cancelled;

    /** Creates an event with the resolved causing entity, or null for environmental damage. */
    public MobDamageEvent(final MobSnapshot mob, final Entity attacker, final double damage) {
        this(mob, attacker, damage, 1.0D);
    }

    /** Creates an event with melee charge captured before the native attack reset. */
    public MobDamageEvent(
        final MobSnapshot mob,
        final Entity attacker,
        final double damage,
        final double attackCharge
    ) {
        this.mob = Objects.requireNonNull(mob, "mob");
        this.attacker = attacker;

        if (!Double.isFinite(attackCharge) || attackCharge < 0 || attackCharge > 1) {
            throw new IllegalArgumentException("attackCharge must be between zero and one");
        }

        this.attackCharge = attackCharge;
        setDamage(damage);
    }

    /** Returns the pre-reset melee charge; non-melee and programmatic hits use one. */
    public double getAttackCharge() {
        return attackCharge;
    }

    /** Returns vanilla melee charge scaling for a replacement custom base damage value. */
    public double getAttackChargeMultiplier() {
        return 0.2D + 0.8D * attackCharge * attackCharge;
    }

    /** Returns the mob snapshot captured before damage is applied. */
    public MobSnapshot getMob() {
        return mob;
    }

    /** Returns the causing entity (projectile shooters are resolved by the runtime). */
    public Optional<Entity> getAttacker() {
        return Optional.ofNullable(attacker);
    }

    /** Returns the mutable custom damage amount. */
    public double getDamage() {
        return damage;
    }

    /** Changes damage; zero is allowed and produces no health mutation. */
    public void setDamage(final double damage) {
        if (!Double.isFinite(damage) || damage < 0) {
            throw new IllegalArgumentException("damage must be finite and non-negative");
        }

        this.damage = damage;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(final boolean cancelled) {
        this.cancelled = cancelled;
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
