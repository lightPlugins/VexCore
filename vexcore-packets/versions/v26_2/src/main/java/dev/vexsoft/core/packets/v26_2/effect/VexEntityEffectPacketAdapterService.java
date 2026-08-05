package dev.vexsoft.core.packets.v26_2.effect;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.packets.display.DisplayGlowColor;
import dev.vexsoft.core.packets.internal.EntityEffectPacketAdapterService;
import dev.vexsoft.core.packets.internal.PacketTransportAdapterService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

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
