package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.service.EntityEffectPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.MobHitPacketService;
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
