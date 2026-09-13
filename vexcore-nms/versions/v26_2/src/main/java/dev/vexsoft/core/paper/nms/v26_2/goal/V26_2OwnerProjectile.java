package dev.vexsoft.core.paper.nms.v26_2.goal;

import dev.vexsoft.core.paper.nms.goal.NmsOwnerMeleeSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.entity.CraftMob;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

/** Native Shulker appearance with owner-bound homing, wall passage, and melee-equivalent damage. */
final class V26_2OwnerProjectile extends ShulkerBullet {

    private final Mob carrier;
    private final Player victim;
    private final NmsOwnerMeleeSpec specification;
    private final OwnerProjectileFlight flight;

    private V26_2OwnerProjectile(final ServerLevel level, final Mob carrier, final Player victim,
        final NmsOwnerMeleeSpec specification) {
        super(EntityTypes.SHULKER_BULLET, level);
        this.carrier = carrier;
        this.victim = victim;
        this.specification = specification;
        flight = new OwnerProjectileFlight(specification.ranged().speed(), specification.ranged().lifetimeTicks());
        var owner = ((CraftMob) carrier).getHandle();
        setOwner(owner);
        setTarget(((CraftPlayer) victim).getHandle());
        Vec3 origin = owner.getBoundingBox().getCenter();
        setPos(origin.x, origin.y, origin.z);
        setNoGravity(true);
        setSilent(true);
        getBukkitEntity().setPersistent(false);
    }

    static Projectile spawn(final Mob mob, final Player target,
        final NmsOwnerMeleeSpec specification) {
        var level = (ServerLevel) ((CraftMob) mob).getHandle().level();
        var projectile = new V26_2OwnerProjectile(level, mob, target, specification);
        if (!level.addFreshEntity(projectile, CreatureSpawnEvent.SpawnReason.CUSTOM)) {
            projectile.discard();
            return null;
        }
        return (Projectile) projectile.getBukkitEntity();
    }

    @Override
    public boolean broadcastToPlayer(final ServerPlayer player) {
        return player.getBukkitEntity().canSee(carrier) && super.broadcastToPlayer(player);
    }

    @Override
    public void tick() {
        if (isRemoved() || !validParticipants()) {
            discard();
            return;
        }
        // Do not call ShulkerBullet.tick: its block raycast, hit damage, and levitation are vanilla-only.
        baseTick();
        Vec3 from = position();
        var targetBox = ((CraftPlayer) victim).getHandle().getBoundingBox().inflate(0.15D);
        flight.tick(!isRemoved(), from, targetBox, next -> {
            if (!level().hasChunkAt(BlockPos.containing(next))) {
                return false;
            }
            setDeltaMovement(next.subtract(from));
            setPos(next.x, next.y, next.z);
            return true;
        }, () -> {
            var hit = new ProjectileHitEvent((Projectile) getBukkitEntity(), victim, null, null);
            Bukkit.getPluginManager().callEvent(hit);
            discard();
            if (!hit.isCancelled() && validParticipants()) {
                victim.damage(specification.damage(), carrier);
            }
        }, this::discard);
    }

    private boolean validParticipants() {
        return carrier.isValid() && !carrier.isDead() && carrier.getTarget() == victim
            && victim.isOnline() && !victim.isDead()
            && victim.getGameMode() != GameMode.CREATIVE && victim.getGameMode() != GameMode.SPECTATOR
            && carrier.getWorld().equals(victim.getWorld())
            && carrier.getWorld().equals(getBukkitEntity().getWorld())
            && carrier.getLocation().distanceSquared(victim.getLocation()) <= specification.radius() * specification.radius();
    }
}
