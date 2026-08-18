package dev.vexsoft.core.paper.service.packets.v26_2;


import dev.vexsoft.core.paper.packets.v26_2.effect.V26_2GlowPackets;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.DisplayGlowColor;
import dev.vexsoft.core.paper.packets.service.EntityEffectPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.PacketTransportAdapterService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

@Dependencies(PacketTransportAdapterService.class)
@SuppressWarnings("fullyQualifiedTypeName") // Bukkit and NMS expose the same simple type name.
public final class VexEntityEffectPacketAdapterService
    implements EntityEffectPacketAdapterService {

  private static final byte HURT_EVENT = 2;

  private final PacketTransportAdapterService transport;
  private final Set<String> glowTeams = ConcurrentHashMap.newKeySet();

  public VexEntityEffectPacketAdapterService(final VexServiceRegistry services) {
    this.transport = services.require(PacketTransportAdapterService.class);
  }

  @Override
  public void playHit(final Player viewer, final LivingEntity target) {
    net.minecraft.world.entity.LivingEntity entity = requireTarget(viewer, target);
    transport.sendBundle(viewer, List.of(
        new ClientboundEntityEventPacket(entity, HURT_EVENT),
        new ClientboundHurtAnimationPacket(entity)
    ));
  }

  @Override
  public void setGlow(
      final Player viewer,
      final LivingEntity target,
      final DisplayGlowColor color
  ) {
    net.minecraft.world.entity.LivingEntity entity = requireTarget(viewer, target);
    boolean created = glowTeams.add(key(viewer.getUniqueId(), entity.getId()));
    transport.sendBundle(viewer, List.of(
        V26_2GlowPackets.metadata(entity, true),
        created
            ? V26_2GlowPackets.addTeam(entity, color)
            : V26_2GlowPackets.updateTeam(entity, color)
    ));
  }

  @Override
  public void clearGlow(final Player viewer, final LivingEntity target) {
    net.minecraft.world.entity.LivingEntity entity = requireTarget(viewer, target);
    if (!glowTeams.remove(key(viewer.getUniqueId(), entity.getId()))) {
      return;
    }
    transport.sendBundle(viewer, List.of(
        V26_2GlowPackets.metadata(entity, entity.hasGlowingTag()),
        V26_2GlowPackets.removeTeam(entity)
    ));
  }

  @Override
  public void strikeLightning(final Player viewer, final LivingEntity target) {
    requireTarget(viewer, target);
    LightningBolt lightning = new LightningBolt(
        EntityTypes.LIGHTNING_BOLT,
        ((CraftWorld) target.getWorld()).getHandle()
    );
    lightning.setPos(target.getX(), target.getY(), target.getZ());
    transport.send(viewer, new ClientboundAddEntityPacket(
        lightning.getId(), lightning.getUUID(), lightning.getX(), lightning.getY(), lightning.getZ(),
        lightning.getXRot(), lightning.getYRot(), lightning.getType(), 0,
        lightning.getDeltaMovement(), lightning.getYHeadRot()
    ));
  }

  @Override
  public void swingHand(
      final Player viewer,
      final Player target,
      final EquipmentSlot hand
  ) {
    net.minecraft.world.entity.LivingEntity entity = requireTarget(viewer, target);
    int animation = switch (hand) {
      case HAND -> 0;
      case OFF_HAND -> 3;
      default -> throw new IllegalArgumentException("Hand must be HAND or OFF_HAND");
    };
    transport.send(viewer, new ClientboundAnimatePacket(entity, animation));
  }

  private static net.minecraft.world.entity.LivingEntity requireTarget(
      final Player viewer,
      final LivingEntity target
  ) {
    if (!viewer.getWorld().equals(target.getWorld())) {
      throw new IllegalArgumentException("Target must be in the viewer's current world");
    }
    return ((CraftLivingEntity) target).getHandle();
  }

  private static String key(final UUID viewerId, final int entityId) {
    return viewerId + ":" + entityId;
  }
}
