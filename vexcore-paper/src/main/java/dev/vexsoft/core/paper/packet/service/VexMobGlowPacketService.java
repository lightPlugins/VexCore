package dev.vexsoft.core.paper.packet.service;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.packets.display.DisplayGlowColor;
import dev.vexsoft.core.packets.internal.EntityEffectPacketAdapterService;
import dev.vexsoft.core.packets.service.MobGlowPacketService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

@Dependencies(EntityEffectPacketAdapterService.class)
public final class VexMobGlowPacketService implements MobGlowPacketService {

  private final EntityEffectPacketAdapterService adapter;

  public VexMobGlowPacketService(final VexServiceRegistry services) {
    this.adapter = services.require(EntityEffectPacketAdapterService.class);
  }

  @Override
  public void setGlow(
      final Player viewer,
      final LivingEntity target,
      final DisplayGlowColor color
  ) {
    adapter.setGlow(viewer, target, color);
  }

  @Override
  public void clearGlow(final Player viewer, final LivingEntity target) {
    adapter.clearGlow(viewer, target);
  }
}
