package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayRequest;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayUpdate;
import dev.vexsoft.core.paper.packets.hologram.HologramInteraction;
import dev.vexsoft.core.paper.packets.hologram.HologramInteractionType;
import dev.vexsoft.core.paper.packets.hologram.InteractableHologramHandle;
import dev.vexsoft.core.paper.packets.hologram.InteractableHologramRequest;
import dev.vexsoft.core.paper.packets.interaction.FakeInteraction;
import dev.vexsoft.core.paper.packets.interaction.FakeInteractionHandle;
import dev.vexsoft.core.paper.packets.interaction.FakeInteractionRequest;
import dev.vexsoft.core.paper.packets.interaction.FakeInteractionType;
import dev.vexsoft.core.paper.packets.service.InteractableHologramService;
import dev.vexsoft.core.paper.packets.service.InteractionPacketService;
import dev.vexsoft.core.paper.packets.service.TextDisplayPacketService;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Backward-compatible text-hologram facade composed from generic virtual display services. */
@Dependencies({TextDisplayPacketService.class, InteractionPacketService.class})
public final class VexInteractableHologramService
    implements InteractableHologramService, AutoCloseable {

  private final ServiceOwner owner;
  private final TextDisplayPacketService textDisplays;
  private final InteractionPacketService interactions;
  private final Map<InteractableHologramHandle, HologramState> holograms =
      new ConcurrentHashMap<>();

  /** Creates the hologram facade through VexCore's service registry. */
  public VexInteractableHologramService(final VexServiceRegistry services) {
    owner = services.getOwner();
    textDisplays = services.require(TextDisplayPacketService.class);
    interactions = services.require(InteractionPacketService.class);
  }

  @Override
  public InteractableHologramHandle spawn(
      final Player viewer,
      final InteractableHologramRequest request
  ) {
    FakeTextDisplayRequest text = request.getTextDisplayRequest();
    FakeDisplayHandle textHandle = textDisplays.spawn(viewer, text);
    FakeInteractionHandle interactionHandle = interactions.spawn(
        viewer,
        FakeInteractionRequest.builder(interactionLocation(
            text.getLocation(),
            request.getHitboxOffset()
        ))
            .width(request.getHitboxWidth())
            .height(request.getHitboxHeight())
            .lifecycle(text.getLifecycle())
            .interactHandler(interaction -> request.getInteractHandler().handle(
                toHologramInteraction(textHandle, interaction)
            ))
            .build()
    );
    InteractableHologramHandle handle = toHologramHandle(textHandle, interactionHandle);
    holograms.put(handle, new HologramState(
        textHandle,
        interactionHandle,
        text.getLocation(),
        request.getHitboxOffset()
    ));
    return handle;
  }

  @Override
  public void update(
      final InteractableHologramHandle handle,
      final FakeTextDisplayUpdate update
  ) {
    state(handle).ifPresent(state -> textDisplays.update(state.textHandle(), update));
  }

  @Override
  public void updateHitbox(
      final InteractableHologramHandle handle,
      final float width,
      final float height
  ) {
    state(handle).ifPresent(state ->
        interactions.updateHitbox(state.interactionHandle(), width, height)
    );
  }

  @Override
  public void updateHitboxOffset(
      final InteractableHologramHandle handle,
      final Vector offset
  ) {
    state(handle).ifPresent(state -> {
      state.hitboxOffset(offset);
      interactions.teleport(
          state.interactionHandle(),
          interactionLocation(state.location(), offset)
      );
    });
  }

  @Override
  public void teleport(final InteractableHologramHandle handle, final Location location) {
    state(handle).ifPresent(state -> {
      state.location(location);
      textDisplays.teleport(state.textHandle(), location);
      interactions.teleport(
          state.interactionHandle(),
          interactionLocation(location, state.hitboxOffset())
      );
    });
  }

  @Override
  public void remove(final InteractableHologramHandle handle) {
    requireOwner(handle);
    HologramState state = holograms.remove(handle);
    if (state == null) {
      return;
    }
    interactions.remove(state.interactionHandle());
    textDisplays.remove(state.textHandle());
  }

  @Override
  public void removeAll(final Player viewer) {
    holograms.keySet().stream()
        .filter(handle -> handle.getViewerId().equals(viewer.getUniqueId()))
        .toList()
        .forEach(this::remove);
  }

  @Override
  public void close() {
    holograms.keySet().stream().toList().forEach(this::remove);
  }

  private Optional<HologramState> state(
      final InteractableHologramHandle handle
  ) {
    requireOwner(handle);
    return Optional.ofNullable(holograms.get(handle));
  }

  private void requireOwner(final InteractableHologramHandle handle) {
    if (!handle.getOwner().equals(owner)) {
      throw new IllegalArgumentException("Hologram handle belongs to another plugin");
    }
  }

  private static HologramInteraction toHologramInteraction(
      final FakeDisplayHandle textHandle,
      final FakeInteraction interaction
  ) {
    return new HologramInteraction(
        interaction.getPlayer(),
        toHologramHandle(textHandle, interaction.getHandle()),
        interaction.getInteractionType() == FakeInteractionType.LEFT_CLICK
            ? HologramInteractionType.LEFT_CLICK
            : HologramInteractionType.RIGHT_CLICK,
        interaction.getHand()
    );
  }

  private static InteractableHologramHandle toHologramHandle(
      final FakeDisplayHandle textHandle,
      final FakeInteractionHandle interactionHandle
  ) {
    return new InteractableHologramHandle(
        textHandle.getOwner(),
        textHandle.getViewerId(),
        textHandle.getEntityId(),
        textHandle.getEntityUuid(),
        interactionHandle.getEntityId(),
        interactionHandle.getEntityUuid()
    );
  }

  private static Location interactionLocation(final Location location, final Vector offset) {
    return location.clone().add(offset);
  }

  private static final class HologramState {
    private final FakeDisplayHandle textHandle;
    private final FakeInteractionHandle interactionHandle;
    private Location location;
    private Vector hitboxOffset;

    private HologramState(
        final FakeDisplayHandle textHandle,
        final FakeInteractionHandle interactionHandle,
        final Location location,
        final Vector hitboxOffset
    ) {
      this.textHandle = textHandle;
      this.interactionHandle = interactionHandle;
      this.location = location.clone();
      this.hitboxOffset = hitboxOffset.clone();
    }

    private FakeDisplayHandle textHandle() {
      return textHandle;
    }

    private FakeInteractionHandle interactionHandle() {
      return interactionHandle;
    }

    private Location location() {
      return location.clone();
    }

    private void location(final Location value) {
      location = value.clone();
    }

    private Vector hitboxOffset() {
      return hitboxOffset.clone();
    }

    private void hitboxOffset(final Vector value) {
      hitboxOffset = value.clone();
    }
  }
}
