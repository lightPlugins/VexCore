package dev.vexsoft.core.paper.packets.v26_2.effect;

import java.util.Optional;
import dev.vexsoft.core.paper.packets.display.DisplayGlowColor;
import dev.vexsoft.core.paper.packets.v26_2.display.V26_2DisplayMapper;
import java.lang.reflect.Field;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

@UtilityClass
public class V26_2GlowPackets {

  private static final byte GLOWING = 0x40;
  private static final EntityDataAccessor<Byte> SHARED_FLAGS = findSharedFlags();

  public static Object metadata(final Entity entity, final boolean glowing) {
    byte flags = entity.getEntityData().get(SHARED_FLAGS);
    byte updated = glowing ? (byte) (flags | GLOWING) : (byte) (flags & ~GLOWING);
    return new ClientboundSetEntityDataPacket(
        entity.getId(),
        List.of(SynchedEntityData.DataValue.create(SHARED_FLAGS, updated))
    );
  }

  public static Object addTeam(final Entity entity, final DisplayGlowColor color) {
    return ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team(entity, color), true);
  }

  public static Object updateTeam(final Entity entity, final DisplayGlowColor color) {
    return ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team(entity, color), false);
  }

  public static Object removeTeam(final Entity entity) {
    return ClientboundSetPlayerTeamPacket.createRemovePacket(
        new PlayerTeam(new Scoreboard(), teamName(entity.getId()))
    );
  }

  private static PlayerTeam team(final Entity entity, final DisplayGlowColor color) {
    Scoreboard scoreboard = new Scoreboard();
    PlayerTeam team = scoreboard.addPlayerTeam(teamName(entity.getId()));
    team.setColor(Optional.of(V26_2DisplayMapper.toNms(color)));
    scoreboard.addPlayerToTeam(entity.getScoreboardName(), team);
    return team;
  }

  private static String teamName(final int entityId) {
    return "vx" + Integer.toUnsignedString(entityId, 36);
  }

  @SuppressWarnings("unchecked")
  private static EntityDataAccessor<Byte> findSharedFlags() {
    for (String name : List.of("DATA_SHARED_FLAGS_ID", "an", "f_19804_")) {
      try {
        Field field = Entity.class.getDeclaredField(name);
        field.setAccessible(true);
        return (EntityDataAccessor<Byte>) field.get(null);
      } catch (ReflectiveOperationException ignored) {
        // Names can differ between development and production mappings
      }
    }
    throw new IllegalStateException("Unable to resolve shared entity flags");
  }
}
