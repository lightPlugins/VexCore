package dev.vexsoft.core.paper.service.packets.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.ServiceReference;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.DisplayLifecycle;
import dev.vexsoft.core.paper.packets.interaction.FakeInteractionHandle;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies viewer isolation, ownership and lifecycle cleanup in the interaction tracker. */
final class VexInteractionTrackerServiceTest {

  @Test
  void isolatesEqualEntityIdsByViewer() {
    TestOwner owner = new TestOwner("plugin");
    VexInteractionTrackerService tracker = tracker(owner);
    UUID firstViewer = UUID.randomUUID();
    UUID secondViewer = UUID.randomUUID();
    TrackedInteraction first = interaction(owner, firstViewer, 42, DisplayLifecycle.PLAYER_QUIT);
    TrackedInteraction second = interaction(owner, secondViewer, 42, DisplayLifecycle.PLAYER_QUIT);

    tracker.track(first);
    tracker.track(second);

    assertEquals(first, tracker.find(firstViewer, 42).orElseThrow());
    assertEquals(second, tracker.find(secondViewer, 42).orElseThrow());
  }

  @Test
  void filtersByOwnerAndLifecycle() {
    TestOwner firstOwner = new TestOwner("first");
    TestOwner secondOwner = new TestOwner("second");
    VexInteractionTrackerService tracker = tracker(firstOwner);
    UUID viewer = UUID.randomUUID();
    TrackedInteraction death = interaction(
        firstOwner,
        viewer,
        1,
        DisplayLifecycle.PLAYER_DEATH
    );
    TrackedInteraction worldChange = interaction(
        firstOwner,
        viewer,
        2,
        DisplayLifecycle.WORLD_CHANGE
    );
    TrackedInteraction otherOwner = interaction(
        secondOwner,
        viewer,
        3,
        DisplayLifecycle.PLAYER_DEATH
    );
    tracker.track(death);
    tracker.track(worldChange);
    tracker.track(otherOwner);

    assertEquals(2, tracker.findOwned(firstOwner, viewer).size());
    assertEquals(2, tracker.removeViewer(viewer, DisplayLifecycle.PLAYER_DEATH).size());
    assertTrue(tracker.find(viewer, 1).isEmpty());
    assertTrue(tracker.find(viewer, 3).isEmpty());
    assertEquals(worldChange, tracker.find(viewer, 2).orElseThrow());
  }

  private static VexInteractionTrackerService tracker(final ServiceOwner owner) {
    return new VexInteractionTrackerService(new TestServices(owner));
  }

  private static TrackedInteraction interaction(
      final ServiceOwner owner,
      final UUID viewerId,
      final int entityId,
      final DisplayLifecycle lifecycle
  ) {
    return new TrackedInteraction(
        new FakeInteractionHandle(owner, viewerId, entityId, UUID.randomUUID()),
        ignored -> { },
        Set.of(lifecycle)
    );
  }

  private record TestOwner(String serviceOwnerName) implements ServiceOwner {

    @Override
    public String getServiceOwnerName() {
      return serviceOwnerName;
    }
  }

  private static final class TestServices implements VexServiceRegistry {

    private final ServiceOwner owner;

    private TestServices(final ServiceOwner owner) {
      this.owner = owner;
    }

    @Override
    public ServiceOwner getOwner() {
      return owner;
    }

    @Override
    public VexServiceRegistry scoped(final ServiceOwner childOwner) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T extends VexService> void register(
        final Class<T> serviceType,
        final Class<? extends T> implementationType
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void registerQueuedServices() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T extends VexService> Optional<T> find(final Class<T> serviceType) {
      return Optional.empty();
    }

    @Override
    public <T extends VexService> T require(final Class<T> serviceType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T extends VexService> ServiceReference<T> reference(final Class<T> serviceType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAvailable(final Class<? extends VexService> serviceType) {
      return false;
    }

    @Override
    public void unregister(final Class<? extends VexService> serviceType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void unregisterOwnedServices() {
      throw new UnsupportedOperationException();
    }
  }
}
