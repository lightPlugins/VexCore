package dev.vexsoft.core.paper.packet.service;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.packets.display.FakePassengerMount;
import dev.vexsoft.core.packets.internal.DisplayPacketAdapterService;
import dev.vexsoft.core.packets.service.DisplayPassengerPacketService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

@Dependencies(DisplayPacketAdapterService.class)
public final class VexDisplayPassengerPacketService implements DisplayPassengerPacketService {

  private final ServiceOwner owner;
  private final DisplayPacketAdapterService adapter;
  private final Map<String, List<FakePassengerMount>> mounts = new ConcurrentHashMap<>();

  public VexDisplayPassengerPacketService(final VexServiceRegistry services) {
    this.owner = services.getOwner();
    this.adapter = services.require(DisplayPacketAdapterService.class);
  }

  @Override
  public void setFakePassengersWithOffset(
      final Player viewer,
      final Entity vehicle,
      final List<FakePassengerMount> passengers
  ) {
    setFakePassengersWithOffset(viewer, vehicle.getEntityId(), passengers);
  }

  @Override
  public void setFakePassengersWithOffset(
      final Player viewer,
      final int vehicleEntityId,
      final List<FakePassengerMount> passengers
  ) {
    List<FakePassengerMount> checked = List.copyOf(passengers);
    checked.forEach(passenger -> applyOffset(viewer, passenger));
    mounts.put(key(viewer, vehicleEntityId), checked);
    sendMounts(viewer, vehicleEntityId, checked);
  }

  @Override
  public void addFakePassenger(
      final Player viewer,
      final Entity vehicle,
      final FakePassengerMount passenger
  ) {
    addFakePassenger(viewer, vehicle.getEntityId(), passenger);
  }

  @Override
  public void addFakePassenger(
      final Player viewer,
      final int vehicleEntityId,
      final FakePassengerMount passenger
  ) {
    String key = key(viewer, vehicleEntityId);
    List<FakePassengerMount> updated = new ArrayList<>(mounts.getOrDefault(key, List.of()));
    updated.removeIf(existing -> existing.getHandle().equals(passenger.getHandle()));
    updated.add(passenger);
    setFakePassengersWithOffset(viewer, vehicleEntityId, updated);
  }

  @Override
  public void removeFakePassenger(
      final Player viewer,
      final Entity vehicle,
      final FakeDisplayHandle passenger
  ) {
    removeFakePassenger(viewer, vehicle.getEntityId(), passenger);
  }

  @Override
  public void removeFakePassenger(
      final Player viewer,
      final int vehicleEntityId,
      final FakeDisplayHandle passenger
  ) {
    String key = key(viewer, vehicleEntityId);
    List<FakePassengerMount> updated = new ArrayList<>(mounts.getOrDefault(key, List.of()));
    updated.removeIf(existing -> existing.getHandle().equals(passenger));
    setFakePassengersWithOffset(viewer, vehicleEntityId, updated);
  }

  @Override
  public void clearFakePassengers(final Player viewer, final Entity vehicle) {
    clearFakePassengers(viewer, vehicle.getEntityId());
  }

  @Override
  public void clearFakePassengers(final Player viewer, final int vehicleEntityId) {
    mounts.remove(key(viewer, vehicleEntityId));
    adapter.setPassengers(viewer, vehicleEntityId, List.of());
  }

  private void sendMounts(
      final Player viewer,
      final int vehicleEntityId,
      final List<FakePassengerMount> passengers
  ) {
    adapter.setPassengers(
        viewer,
        vehicleEntityId,
        passengers.stream().map(FakePassengerMount::getHandle)
            .map(FakeDisplayHandle::getEntityId)
            .toList()
    );
  }

  private void applyOffset(final Player viewer, final FakePassengerMount passenger) {
    FakeDisplayHandle handle = passenger.getHandle();
    if (!handle.getOwner().equals(owner)) {
      throw new IllegalArgumentException("Passenger display belongs to another plugin");
    }
    adapter.setTranslation(
        viewer, handle, passenger.getOffsetX(), passenger.getOffsetY(), passenger.getOffsetZ()
    );
  }

  private static String key(final Player viewer, final int vehicleEntityId) {
    return viewer.getUniqueId() + ":" + vehicleEntityId;
  }
}
