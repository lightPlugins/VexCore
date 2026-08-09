package dev.vexsoft.core.paper.reactor.context;

import dev.vexsoft.core.api.player.VexPlayer;
import java.util.Objects;
import lombok.Getter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

@Getter
public final class PaperDamageEntityContext implements DamageEntityReactorContext {

  private final VexPlayer player;
  private final EntityDamageByEntityEvent event;
  private final Entity target;
  private final ItemStack item;

  /** Creates a context backed by one entity-damage event. */
  public PaperDamageEntityContext(
      final VexPlayer player,
      final Player attacker,
      final EntityDamageByEntityEvent event
  ) {
    this.player = Objects.requireNonNull(player, "player");
    this.event = Objects.requireNonNull(event, "event");
    target = event.getEntity();
    item = Objects.requireNonNull(attacker, "attacker").getInventory().getItemInMainHand();
  }

  @Override
  public double getDamage() {
    return event.getDamage();
  }

  @Override
  public EntityDamageEvent.DamageCause getDamageCause() {
    return event.getCause();
  }

  @Override
  public void setDamage(final double damage) {
    if (!Double.isFinite(damage) || damage < 0D) {
      throw new IllegalArgumentException("Damage must be finite and non-negative");
    }
    event.setDamage(damage);
  }

  @Override
  public boolean isCancelled() {
    return event.isCancelled();
  }

  @Override
  public void setCancelled(final boolean cancelled) {
    event.setCancelled(cancelled);
  }

  @Override
  public Object getVariable(final String name) {
    return switch (name) {
      case "damage" -> getDamage();
      case "player-health" -> event.getDamager() instanceof Player attacker
          ? attacker.getHealth() : null;
      case "victim-health" -> target instanceof LivingEntity living ? living.getHealth() : null;
      default -> null;
    };
  }
}
