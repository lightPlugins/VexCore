package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.FakePassengerMount;
import java.util.List;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Mounts viewer-specific fake displays onto real or virtual entities
 */
public interface DisplayPassengerPacketService extends VexService {

  /** Replaces the fake passengers mounted onto a real entity */
  default void setFakePassengers(
      final Player viewer,
      final Entity vehicle,
      final List<FakeDisplayHandle> passengers
  ) {
    setFakePassengersWithOffset(viewer, vehicle, passengers.stream()
        .map(FakePassengerMount::of)
        .toList());
  }

  /** Replaces the fake passengers mounted onto an entity id */
  default void setFakePassengers(
      final Player viewer,
      final int vehicleEntityId,
      final List<FakeDisplayHandle> passengers
  ) {
    setFakePassengersWithOffset(viewer, vehicleEntityId, passengers.stream()
        .map(FakePassengerMount::of)
        .toList());
  }

  /** Replaces the offset fake passengers mounted onto a real entity */
  void setFakePassengersWithOffset(
      Player viewer,
      Entity vehicle,
      List<FakePassengerMount> passengers
  );

  /** Replaces the offset fake passengers mounted onto an entity id */
  void setFakePassengersWithOffset(
      Player viewer,
      int vehicleEntityId,
      List<FakePassengerMount> passengers
  );

  /** Adds one fake passenger to a real entity */
  default void addFakePassenger(
      final Player viewer,
      final Entity vehicle,
      final FakeDisplayHandle passenger
  ) {
    addFakePassenger(viewer, vehicle, FakePassengerMount.of(passenger));
  }

  /** Adds one fake passenger to an entity id */
  default void addFakePassenger(
      final Player viewer,
      final int vehicleEntityId,
      final FakeDisplayHandle passenger
  ) {
    addFakePassenger(viewer, vehicleEntityId, FakePassengerMount.of(passenger));
  }

  /** Adds one offset fake passenger to a real entity */
  void addFakePassenger(Player viewer, Entity vehicle, FakePassengerMount passenger);

  /** Adds one offset fake passenger to an entity id */
  void addFakePassenger(Player viewer, int vehicleEntityId, FakePassengerMount passenger);

  /** Removes one fake passenger from a real entity */
  void removeFakePassenger(Player viewer, Entity vehicle, FakeDisplayHandle passenger);

  /** Removes one fake passenger from an entity id */
  void removeFakePassenger(Player viewer, int vehicleEntityId, FakeDisplayHandle passenger);

  /** Removes every fake passenger mounted onto a real entity */
  void clearFakePassengers(Player viewer, Entity vehicle);

  /** Removes every fake passenger mounted onto an entity id */
  void clearFakePassengers(Player viewer, int vehicleEntityId);
}
