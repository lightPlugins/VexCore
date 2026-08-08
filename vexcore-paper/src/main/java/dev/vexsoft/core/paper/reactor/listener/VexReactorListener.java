package dev.vexsoft.core.paper.reactor.listener;

import dev.vexsoft.core.paper.reactor.context.PaperBreakBlockContext;
import dev.vexsoft.core.paper.reactor.context.PaperDamageEntityContext;
import dev.vexsoft.core.paper.reactor.context.PaperKillContext;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.ReactorEngine;
import dev.vexsoft.core.paper.player.PaperPlayerService;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

@Dependencies({ReactorEngine.class, PaperPlayerService.class})
public final class VexReactorListener implements Listener {

  private final ReactorEngine reactions;
  private final PaperPlayerService players;

  public VexReactorListener(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    reactions = checkedServices.require(ReactorEngine.class);
    players = checkedServices.require(PaperPlayerService.class);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onBlockBreak(final BlockBreakEvent event) {
    players.find(event.getPlayer()).ifPresent(player -> reactions.dispatch(
        "break-block",
        new PaperBreakBlockContext(player, event)
    ));
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onEntityDamage(final EntityDamageByEntityEvent event) {
    if (!(event.getDamager() instanceof Player attacker)) {
      return;
    }
    players.find(attacker).ifPresent(player -> reactions.dispatch(
        "damage-entity",
        new PaperDamageEntityContext(player, attacker, event)
    ));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onEntityDeath(final EntityDeathEvent event) {
    Player killer = event.getEntity().getKiller();
    if (killer == null) {
      return;
    }
    VexPlayer player = players.find(killer).orElse(null);
    if (player != null) {
      reactions.dispatch("kill", new PaperKillContext(player, event.getEntity()));
    }
  }
}
