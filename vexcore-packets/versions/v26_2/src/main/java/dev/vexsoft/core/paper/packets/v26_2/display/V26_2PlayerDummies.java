package dev.vexsoft.core.paper.packets.v26_2.display;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.mojang.datafixers.util.Pair;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.dummy.SkinTexture;
import dev.vexsoft.core.paper.packets.service.PacketTransportAdapterService;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftMannequin;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Mannequins never join the world or tick; only their packets reach their owner. */
public final class V26_2PlayerDummies {
  private final PacketTransportAdapterService transport;
  private final Map<FakeDisplayHandle, Mannequin> entities = new ConcurrentHashMap<>();

  public V26_2PlayerDummies(PacketTransportAdapterService transport) {
    this.transport = transport;
  }

  public void spawn(Player viewer, FakeDisplayHandle handle, Location center) {
    var entity = new Mannequin(EntityTypes.MANNEQUIN, ((CraftWorld) center.getWorld()).getHandle());
    entity.setId(handle.getEntityId());
    entity.setUUID(handle.getEntityUuid());
    entity.setImmovable(true);
    entity.setNoGravity(true);
    entity.setInvulnerable(true);
    entity.setHideDescription(true);
    entity.setCustomNameVisible(false);
    ((CraftMannequin) entity.getBukkitEntity()).setCollidable(false);
    position(entity, center);
    var team = team(entity);
    var packets = new ArrayList<Object>();
    packets.add(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
    packets.addAll(V26_2DisplayPackets.spawn(entity));
    transport.sendBundle(viewer, packets);
    entities.put(handle, entity);
  }

  public void skin(Player viewer, FakeDisplayHandle handle, SkinTexture skin) {
    var entity = entities.get(handle);
    if (entity == null) {
      return;
    }
    var builder = ResolvableProfile.resolvableProfile();
    builder.addProperty(
        skin.signature() == null
            ? new ProfileProperty("textures", skin.value())
            : new ProfileProperty("textures", skin.value(), skin.signature()));
    ((CraftMannequin) entity.getBukkitEntity()).setProfile(builder.build());
    Object update = V26_2DisplayPackets.metadata(entity);
    if (update != null) {
      transport.sendBundle(viewer, List.of(update));
    }
  }

  public void armor(Player viewer, FakeDisplayHandle handle, ItemStack[] armor) {
    if (!entities.containsKey(handle)) {
      return;
    }
    var slots =
        new EquipmentSlot[] {
          EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
        };
    var items =
        new ArrayList<>(
            List.of(
                Pair.of(EquipmentSlot.MAINHAND, CraftItemStack.asNMSCopy((ItemStack) null)),
                Pair.of(EquipmentSlot.OFFHAND, CraftItemStack.asNMSCopy((ItemStack) null))));
    for (int i = 0; i < 4; i++) {
      items.add(Pair.of(slots[i], CraftItemStack.asNMSCopy(armor[i])));
    }
    transport.sendBundle(
        viewer, List.of(new ClientboundSetEquipmentPacket(handle.getEntityId(), items)));
  }

  public void move(Player viewer, FakeDisplayHandle handle, Location center) {
    var entity = entities.get(handle);
    if (entity == null) {
      return;
    }
    position(entity, center);
    Location feet = center.clone().subtract(0, entity.getBbHeight() / 2.0, 0);
    transport.sendBundle(
        viewer,
        List.of(
            V26_2DisplayPackets.teleport(entity.getId(), feet),
            new ClientboundRotateHeadPacket(entity, (byte) (center.getYaw() * 256 / 360))));
  }

  public void remove(Player viewer, FakeDisplayHandle handle) {
    var entity = entities.remove(handle);
    if (entity == null || viewer == null) {
      return;
    }
    transport.sendBundle(
        viewer,
        List.of(
            V26_2DisplayPackets.remove(entity.getId()),
            ClientboundSetPlayerTeamPacket.createRemovePacket(team(entity))));
  }

  private static void position(Mannequin entity, Location center) {
    entity.setPos(center.getX(), center.getY() - entity.getBbHeight() / 2.0, center.getZ());
    entity.setYRot(center.getYaw());
    entity.setYHeadRot(center.getYaw());
    entity.setYBodyRot(center.getYaw());
  }

  private static PlayerTeam team(Mannequin entity) {
    var scoreboard = new Scoreboard();
    var team = scoreboard.addPlayerTeam("vxd" + Integer.toUnsignedString(entity.getId(), 36));
    team.setCollisionRule(Team.CollisionRule.NEVER);
    team.setNameTagVisibility(Team.Visibility.NEVER);
    scoreboard.addPlayerToTeam(entity.getScoreboardName(), team);
    return team;
  }
}
