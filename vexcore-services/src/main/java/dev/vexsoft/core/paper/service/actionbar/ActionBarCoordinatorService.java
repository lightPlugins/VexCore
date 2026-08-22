package dev.vexsoft.core.paper.service.actionbar;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Coordinates action-bar channels across all owner-scoped Vex plugins. */
public interface ActionBarCoordinatorService extends VexService {

  /** Sets or replaces one persistent owner channel. */
  void setPersistent(
      ServiceOwner owner,
      Player player,
      String channel,
      Component component,
      int priority
  );

  /** Sets or replaces one temporary owner channel. */
  void showTemporary(
      ServiceOwner owner,
      Player player,
      String channel,
      Component component,
      long durationTicks,
      int priority
  );

  /** Removes one persistent owner channel. */
  void clearPersistent(ServiceOwner owner, Player player, String channel);

  /** Removes one temporary owner channel. */
  void clearTemporary(ServiceOwner owner, Player player, String channel);

  /** Removes every channel belonging to an owner for one player. */
  void clear(ServiceOwner owner, Player player);

  /** Removes every channel belonging to an owner across all players. */
  void clearOwner(ServiceOwner owner);
}
