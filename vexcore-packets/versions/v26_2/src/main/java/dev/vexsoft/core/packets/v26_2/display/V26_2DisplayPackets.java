package dev.vexsoft.core.packets.v26_2.display;

import dev.vexsoft.core.packets.display.DisplayBrightness;
import dev.vexsoft.core.packets.display.DisplayTransformation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.joml.Vector3f;

@UtilityClass
public class V26_2DisplayPackets {

  private static final EntityDataAccessor<Vector3f> TRANSLATION = findTranslation();

  public static void applyBase(
      final Display display,
      final DisplayTransformation transformation,
      final dev.vexsoft.core.packets.display.DisplayBillboard billboard,
      final DisplayBrightness brightness,
      final float viewRange,
      final float shadowRadius,
      final float shadowStrength,
      final float width,
      final float height,
      final int interpolationDelay,
      final int interpolationDuration,
      final int teleportDuration
  ) {
    display.setTransformation(V26_2DisplayMapper.toNms(transformation));
    display.setBillboardConstraints(V26_2DisplayMapper.toNms(billboard));
    if (brightness != null) {
      display.setBrightnessOverride(V26_2DisplayMapper.toNms(brightness));
    }
    display.setViewRange(viewRange);
    display.setShadowRadius(shadowRadius);
    display.setShadowStrength(shadowStrength);
    display.setWidth(width);
    display.setHeight(height);
    display.setTransformationInterpolationDelay(interpolationDelay);
    display.setTransformationInterpolationDuration(interpolationDuration);
    display.getEntityData().set(Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID, teleportDuration, true);
  }

  public static List<Object> spawn(final Entity entity) {
    List<Object> packets = new ArrayList<>();
    packets.add(new ClientboundAddEntityPacket(
        entity.getId(), entity.getUUID(), entity.getX(), entity.getY(), entity.getZ(),
        entity.getXRot(), entity.getYRot(), entity.getType(), 0,
        entity.getDeltaMovement(), entity.getYHeadRot()
    ));
    packets.add(new ClientboundSetEntityDataPacket(entity.getId(), entity.getEntityData().packAll()));
    return packets;
  }

  public static Object metadata(final Entity entity) {
    List<SynchedEntityData.DataValue<?>> values = entity.getEntityData().packDirty();
    if (values == null || values.isEmpty()) {
      return null;
    }
    return new ClientboundSetEntityDataPacket(entity.getId(), values);
  }

  public static Object teleport(final int entityId, final Location location) {
    PositionMoveRotation position = new PositionMoveRotation(
        new Vec3(location.getX(), location.getY(), location.getZ()),
        Vec3.ZERO,
        location.getYaw(),
        location.getPitch()
    );
    return ClientboundTeleportEntityPacket.teleport(entityId, position, Set.<Relative>of(), false);
  }

  public static Object remove(final int... entityIds) {
    return new ClientboundRemoveEntitiesPacket(entityIds);
  }

  public static void setTranslation(final Display display, final Vector3f translation) {
    display.getEntityData().set(TRANSLATION, new Vector3f(translation), true);
  }

  @SuppressWarnings("unchecked")
  private static EntityDataAccessor<Vector3f> findTranslation() {
    for (String name : List.of("DATA_TRANSLATION_ID", "s")) {
      try {
        Field field = Display.class.getDeclaredField(name);
        field.setAccessible(true);
        return (EntityDataAccessor<Vector3f>) field.get(null);
      } catch (ReflectiveOperationException ignored) {
        // Names can differ between development and production mappings
      }
    }
    throw new IllegalStateException("Unable to resolve the display translation field");
  }
}
