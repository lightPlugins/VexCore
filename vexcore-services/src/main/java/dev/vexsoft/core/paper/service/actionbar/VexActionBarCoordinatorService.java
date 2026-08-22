package dev.vexsoft.core.paper.service.actionbar;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Default cross-plugin action-bar channel coordinator. */
@Dependencies(ScheduleService.class)
public final class VexActionBarCoordinatorService implements
    ActionBarCoordinatorService,
    AutoCloseable {

  private static final long UPDATE_INTERVAL_TICKS = 5L;
  private static final long FORCED_REFRESH_NANOS = 2_000_000_000L;
  private static final long NANOS_PER_TICK = 50_000_000L;

  private final ScheduleService schedules;
  private final Map<UUID, ManagedState> states = new ConcurrentHashMap<>();
  private final AtomicLong sequence = new AtomicLong();

  /** Resolves VexCore's Folia-safe scheduler. */
  public VexActionBarCoordinatorService(final VexServiceRegistry services) {
    schedules = Objects.requireNonNull(services, "services").require(ScheduleService.class);
  }

  @Override
  public void setPersistent(
      final ServiceOwner owner,
      final Player player,
      final String channel,
      final Component component,
      final int priority
  ) {
    ManagedState state = state(player);
    state.channels().setPersistent(owner, channel, component, priority, sequence.incrementAndGet());
    refreshSoon(player, state);
  }

  @Override
  public void showTemporary(
      final ServiceOwner owner,
      final Player player,
      final String channel,
      final Component component,
      final long durationTicks,
      final int priority
  ) {
    if (durationTicks < 1L) {
      throw new IllegalArgumentException("durationTicks must be at least one tick");
    }
    long durationNanos;
    try {
      durationNanos = Math.multiplyExact(durationTicks, NANOS_PER_TICK);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("durationTicks is too large", exception);
    }
    long now = System.nanoTime();
    long expiresAt = durationNanos > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + durationNanos;
    ManagedState state = state(player);
    state.channels().showTemporary(
        owner,
        channel,
        component,
        priority,
        sequence.incrementAndGet(),
        expiresAt
    );
    refreshSoon(player, state);
  }

  @Override
  public void clearPersistent(
      final ServiceOwner owner,
      final Player player,
      final String channel
  ) {
    ManagedState state = states.get(requirePlayer(player).getUniqueId());
    if (state != null && state.channels().clearPersistent(owner, channel)) {
      refreshSoon(player, state);
    }
  }

  @Override
  public void clearTemporary(
      final ServiceOwner owner,
      final Player player,
      final String channel
  ) {
    ManagedState state = states.get(requirePlayer(player).getUniqueId());
    if (state != null && state.channels().clearTemporary(owner, channel)) {
      refreshSoon(player, state);
    }
  }

  @Override
  public void clear(final ServiceOwner owner, final Player player) {
    ManagedState state = states.get(requirePlayer(player).getUniqueId());
    if (state != null && state.channels().clear(owner)) {
      refreshSoon(player, state);
    }
  }

  @Override
  public void clearOwner(final ServiceOwner owner) {
    Objects.requireNonNull(owner, "owner");
    states.values().forEach(state -> {
      if (state.channels().clear(owner)) {
        refreshSoon(state.player(), state);
      }
    });
  }

  @Override
  public void close() {
    states.values().forEach(state -> {
      state.cancel();
    });
    states.clear();
  }

  private ManagedState state(final Player player) {
    Player checkedPlayer = requirePlayer(player);
    ManagedState state = states.computeIfAbsent(
        checkedPlayer.getUniqueId(),
        ignored -> new ManagedState(checkedPlayer)
    );
    state.updatePlayer(checkedPlayer);
    state.start(schedules, () -> refresh(checkedPlayer, state), () -> retire(state));
    return state;
  }

  private void refreshSoon(final Player player, final ManagedState state) {
    schedules.runFor(player, () -> refresh(player, state), () -> retire(state));
  }

  private void refresh(final Player player, final ManagedState state) {
    if (states.get(player.getUniqueId()) != state) {
      return;
    }
    long now = System.nanoTime();
    ActionBarChannelState.Selection selection = state.channels().select(now);
    if (selection.selected().isEmpty()) {
      if (state.wasVisible()) {
        player.sendActionBar(Component.empty());
      }
      if (states.remove(player.getUniqueId(), state)) {
        state.cancel();
      }
      return;
    }
    Component component = selection.selected().orElseThrow();
    if (state.shouldSend(component, selection.revision(), now, FORCED_REFRESH_NANOS)) {
      player.sendActionBar(component);
      state.sent(component, selection.revision(), now);
    }
  }

  private void retire(final ManagedState state) {
    states.remove(state.player().getUniqueId(), state);
    state.cancel();
  }

  private static Player requirePlayer(final Player player) {
    return Objects.requireNonNull(player, "player");
  }

  private static final class ManagedState {

    private final ActionBarChannelState channels = new ActionBarChannelState();
    private volatile Player player;
    private VexTask task;
    private boolean starting;
    private Component lastComponent;
    private long lastRevision = -1L;
    private long lastSentAt;

    private ManagedState(final Player player) {
      this.player = player;
    }

    ActionBarChannelState channels() {
      return channels;
    }

    Player player() {
      return player;
    }

    void updatePlayer(final Player updatedPlayer) {
      player = updatedPlayer;
    }

    synchronized void start(
        final ScheduleService schedules,
        final Runnable update,
        final Runnable retired
    ) {
      if (task != null || starting) {
        return;
      }
      starting = true;
      task = schedules.runForTimer(
          player,
          1L,
          UPDATE_INTERVAL_TICKS,
          update,
          retired
      ).orElse(null);
      starting = false;
    }

    synchronized boolean shouldSend(
        final Component component,
        final long revision,
        final long now,
        final long forcedRefreshNanos
    ) {
      return revision != lastRevision || !component.equals(lastComponent)
          || now - lastSentAt >= forcedRefreshNanos;
    }

    synchronized void sent(final Component component, final long revision, final long now) {
      lastComponent = component;
      lastRevision = revision;
      lastSentAt = now;
    }

    synchronized boolean wasVisible() {
      return lastComponent != null;
    }

    synchronized void cancel() {
      if (task != null) {
        task.cancel();
        task = null;
      }
      starting = false;
    }
  }
}
