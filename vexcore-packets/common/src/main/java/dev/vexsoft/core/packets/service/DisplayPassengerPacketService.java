package dev.vexsoft.core.packets.service;

import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.packets.display.FakePassengerMount;
import java.util.List;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Mounts viewer-specific fake displays onto real or virtual entities
 */
public interface DisplayPassengerPacketService extends VexService {

  /** Replaces the fake passengers mounted onto a real entity */
  public default void setFakePassengers(
      final Player viewer,
      final Entity vehicle,
      final List<FakeDisplayHandle> passengers
  ) {
    setFakePassengersWithOffset(viewer, vehicle, passengers.stream()
        .map(FakePassengerMount::of)
        .toList());
  }

  /** Replaces the fake passengers mounted onto an entity id */
  public default void setFakePassengers(
      final Player viewer,
      final int vehicleEntityId,
      final List<FakeDisplayHandle> passengers
  ) {
    setFakePassengersWithOffset(viewer, vehicleEntityId, passengers.stream()
        .map(FakePassengerMount::of)
        .toList());
  }

  /** Replaces the offset fake passengers mounted onto a real entity */
  public void setFakePassengersWithOffset(
      Player viewer,
      Entity vehicle,
      List<FakePassengerMount> passengers
  );

  /** Replaces the offset fake passengers mounted onto an entity id */
  public void setFakePassengersWithOffset(
      Player viewer,
      int vehicleEntityId,
      List<FakePassengerMount> passengers
  );

  /** Adds one fake passenger to a real entity */
  public default void addFakePassenger(
      final Player viewer,
      final Entity vehicle,
      final FakeDisplayHandle passenger
  ) {
    addFakePassenger(viewer, vehicle, FakePassengerMount.of(passenger));
  }

  /** Adds one fake passenger to an entity id */
  public default void addFakePassenger(
      final Player viewer,
      final int vehicleEntityId,
      final FakeDisplayHandle passenger
  ) {
    addFakePassenger(viewer, vehicleEntityId, FakePassengerMount.of(passenger));
  }

  /** Adds one offset fake passenger to a real entity */
  public void addFakePassenger(Player viewer, Entity vehicle, FakePassengerMount passenger);

  /** Adds one offset fake passenger to an entity id */
  public void addFakePassenger(Player viewer, int vehicleEntityId, FakePassengerMount passenger);

  /** Removes one fake passenger from a real entity */
  public void removeFakePassenger(Player viewer, Entity vehicle, FakeDisplayHandle passenger);

  /** Removes one fake passenger from an entity id */
  public void removeFakePassenger(Player viewer, int vehicleEntityId, FakeDisplayHandle passenger);

  /** Removes every fake passenger mounted onto a real entity */
  public void clearFakePassengers(Player viewer, Entity vehicle);

  /** Removes every fake passenger mounted onto an entity id */
  public void clearFakePassengers(Player viewer, int vehicleEntityId);
}
