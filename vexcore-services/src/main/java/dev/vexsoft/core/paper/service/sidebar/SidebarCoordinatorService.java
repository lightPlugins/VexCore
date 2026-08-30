package dev.vexsoft.core.paper.service.sidebar;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import org.bukkit.entity.Player;

/** Coordinates sidebar channels across every owner-scoped Vex plugin. */
public interface SidebarCoordinatorService extends VexService {

  void setPersistent(
      ServiceOwner owner,
      Player player,
      String channel,
      SidebarFrame frame,
      int priority
  );

  void showTemporary(
      ServiceOwner owner,
      Player player,
      String channel,
      SidebarFrame frame,
      long durationTicks,
      int priority
  );

  void clearPersistent(ServiceOwner owner, Player player, String channel);

  void clearTemporary(ServiceOwner owner, Player player, String channel);

  void clear(ServiceOwner owner, Player player);

  void clearOwner(ServiceOwner owner);
}
