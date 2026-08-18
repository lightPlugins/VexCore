package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.service.EntityEffectPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.PlayerAnimationPacketService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/** Default viewer-scoped player-animation service backed by the selected packet adapter. */
@Dependencies(EntityEffectPacketAdapterService.class)
public final class VexPlayerAnimationPacketService implements PlayerAnimationPacketService {

  private final EntityEffectPacketAdapterService adapter;

  /** Creates the animation service through VexCore's service registry. */
  public VexPlayerAnimationPacketService(final VexServiceRegistry services) {
    adapter = services.require(EntityEffectPacketAdapterService.class);
  }

  @Override
  public void swingHand(
      final Player viewer,
      final Player target,
      final EquipmentSlot hand
  ) {
    adapter.swingHand(viewer, target, hand);
  }
}
