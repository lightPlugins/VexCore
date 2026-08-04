package dev.vexsoft.core.paper.packet.service;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.packets.internal.EntityEffectPacketAdapterService;
import dev.vexsoft.core.packets.service.MobHitPacketService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

@Dependencies(EntityEffectPacketAdapterService.class)
public final class VexMobHitPacketService implements MobHitPacketService {

  private final EntityEffectPacketAdapterService adapter;

  public VexMobHitPacketService(final VexServiceRegistry services) {
    this.adapter = services.require(EntityEffectPacketAdapterService.class);
  }

  @Override
  public void playHit(final Player viewer, final LivingEntity target) {
    adapter.playHit(viewer, target);
  }
}
