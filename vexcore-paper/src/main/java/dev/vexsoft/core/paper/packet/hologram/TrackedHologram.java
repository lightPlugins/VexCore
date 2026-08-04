package dev.vexsoft.core.paper.packet.hologram;

import dev.vexsoft.core.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.packets.hologram.HologramInteractHandler;
import dev.vexsoft.core.packets.hologram.InteractableHologramHandle;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import java.util.Set;
import dev.vexsoft.core.packets.display.DisplayLifecycle;

@Getter
public final class TrackedHologram {

  private final InteractableHologramHandle handle;
  private final FakeDisplayHandle textDisplayHandle;
  private final HologramInteractHandler interactHandler;
  private final Set<DisplayLifecycle> lifecycle;
  @Setter
  private Location location;
  @Setter
  private Vector hitboxOffset;

  public TrackedHologram(
      final InteractableHologramHandle handle,
      final FakeDisplayHandle textDisplayHandle,
      final HologramInteractHandler interactHandler,
      final Location location,
      final Vector hitboxOffset,
      final Set<DisplayLifecycle> lifecycle
  ) {
    this.handle = handle;
    this.textDisplayHandle = textDisplayHandle;
    this.interactHandler = interactHandler;
    this.location = location.clone();
    this.hitboxOffset = hitboxOffset.clone();
    this.lifecycle = Set.copyOf(lifecycle);
  }

  public Location getLocation() {
    return location.clone();
  }

  public Vector getHitboxOffset() {
    return hitboxOffset.clone();
  }
}
