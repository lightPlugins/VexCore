package dev.vexsoft.core.packets.v26_2.display;

import java.lang.reflect.Field;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.world.entity.Entity;

@UtilityClass
public class V26_2PassengerPackets {

  private static final Field VEHICLE = findField("vehicle", "f_133272_", "b");
  private static final Field PASSENGERS = findField("passengers", "f_133273_", "c");

  public static Object create(
      final Entity packetVehicle,
      final int vehicleEntityId,
      final int[] passengerEntityIds
  ) {
    ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(packetVehicle);
    try {
      VEHICLE.setInt(packet, vehicleEntityId);
      PASSENGERS.set(packet, passengerEntityIds.clone());
      return packet;
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException("Unable to create fake passenger packet", exception);
    }
  }

  private static Field findField(final String... names) {
    for (String name : names) {
      try {
        Field field = ClientboundSetPassengersPacket.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
      } catch (ReflectiveOperationException ignored) {
        // Names can differ between development and production mappings
      }
    }
    throw new IllegalStateException(
        "Unable to resolve ClientboundSetPassengersPacket field " + List.of(names)
    );
  }
}
