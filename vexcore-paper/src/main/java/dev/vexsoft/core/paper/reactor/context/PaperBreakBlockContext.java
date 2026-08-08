package dev.vexsoft.core.paper.reactor.context;

import dev.vexsoft.core.api.player.VexPlayer;
import java.util.Objects;
import lombok.Getter;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

@Getter
public final class PaperBreakBlockContext implements BreakBlockReactorContext {

  private final VexPlayer player;
  private final BlockBreakEvent event;
  private final Block block;
  private final ItemStack item;

  /** Creates a context backed by one block-break event. */
  public PaperBreakBlockContext(final VexPlayer player, final BlockBreakEvent event) {
    this.player = Objects.requireNonNull(player, "player");
    this.event = Objects.requireNonNull(event, "event");
    block = event.getBlock();
    item = event.getPlayer().getInventory().getItemInMainHand();
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
      case "player-health" -> event.getPlayer().getHealth();
      case "block-x" -> block.getX();
      case "block-y" -> block.getY();
      case "block-z" -> block.getZ();
      default -> null;
    };
  }
}
