package dev.vexsoft.core.paper.service.sidebar;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

/** Cross-plugin, player-local sidebar channel coordinator. */
@Dependencies(ScheduleService.class)
public final class VexSidebarCoordinatorService
    implements SidebarCoordinatorService, AutoCloseable {

  private static final long NANOS_PER_TICK = 50_000_000L;
  private static final String[] ENTRIES = {
      "vex_line_0", "vex_line_1", "vex_line_2", "vex_line_3", "vex_line_4",
      "vex_line_5", "vex_line_6", "vex_line_7", "vex_line_8", "vex_line_9",
      "vex_line_10", "vex_line_11", "vex_line_12", "vex_line_13", "vex_line_14"
  };

  private final ScheduleService schedules;
  private final Map<UUID, ManagedState> states = new ConcurrentHashMap<>();
  private final AtomicLong sequence = new AtomicLong();

  public VexSidebarCoordinatorService(final VexServiceRegistry services) {
    schedules = Objects.requireNonNull(services, "services").require(ScheduleService.class);
  }

  @Override
  public void setPersistent(
      final ServiceOwner owner,
      final Player player,
      final String channel,
      final SidebarFrame frame,
      final int priority
  ) {
    ManagedState state = state(player);
    state.channels.setPersistent(owner, channel, frame, priority, sequence.incrementAndGet());
    refreshSoon(state);
  }

  @Override
  public void showTemporary(
      final ServiceOwner owner,
      final Player player,
      final String channel,
      final SidebarFrame frame,
      final long durationTicks,
      final int priority
  ) {
    if (durationTicks < 1L) {
      throw new IllegalArgumentException("durationTicks must be positive");
    }
    long duration;
    try {
      duration = Math.multiplyExact(durationTicks, NANOS_PER_TICK);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("durationTicks is too large", exception);
    }
    long now = System.nanoTime();
    long expires = duration > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + duration;
    ManagedState state = state(player);
    state.channels.showTemporary(
        owner, channel, frame, priority, sequence.incrementAndGet(), expires
    );
    refreshSoon(state);
    schedules.runForLater(
        state.player,
        durationTicks,
        () -> refresh(state),
        () -> retire(state)
    ).ifPresentOrElse(ignored -> { }, () -> retire(state));
  }

  @Override
  public void clearPersistent(
      final ServiceOwner owner,
      final Player player,
      final String channel
  ) {
    ManagedState state = states.get(player.getUniqueId());
    if (state != null && state.channels.clearPersistent(owner, channel)) {
      refreshSoon(state);
    }
  }

  @Override
  public void clearTemporary(
      final ServiceOwner owner,
      final Player player,
      final String channel
  ) {
    ManagedState state = states.get(player.getUniqueId());
    if (state != null && state.channels.clearTemporary(owner, channel)) {
      refreshSoon(state);
    }
  }

  @Override
  public void clear(final ServiceOwner owner, final Player player) {
    ManagedState state = states.get(player.getUniqueId());
    if (state != null && state.channels.clear(owner)) {
      refreshSoon(state);
    }
  }

  @Override
  public void clearOwner(final ServiceOwner owner) {
    states.values().forEach(state -> {
      if (state.channels.clear(owner)) {
        refreshSoon(state);
      }
    });
  }

  @Override
  public void close() {
    states.values().forEach(ManagedState::close);
    states.clear();
  }

  private ManagedState state(final Player player) {
    Player checked = Objects.requireNonNull(player, "player");
    ManagedState state = states.computeIfAbsent(
        checked.getUniqueId(), ignored -> new ManagedState(checked)
    );
    state.player = checked;
    return state;
  }

  private void refreshSoon(final ManagedState state) {
    schedules.runFor(
        state.player,
        () -> refresh(state),
        () -> retire(state)
    ).ifPresentOrElse(ignored -> { }, () -> retire(state));
  }

  private void refresh(final ManagedState state) {
    if (states.get(state.player.getUniqueId()) != state) {
      return;
    }
    SidebarChannelState.Selection selected = state.channels.select(System.nanoTime());
    if (selected.frame() == null) {
      if (states.remove(state.player.getUniqueId(), state)) {
        state.close();
      }
      return;
    }
    state.render(selected.frame());
  }

  private void retire(final ManagedState state) {
    states.remove(state.player.getUniqueId(), state);
    state.close();
  }

  private static final class ManagedState {
    private final SidebarChannelState channels = new SidebarChannelState();
    private Player player;
    private Scoreboard previous;
    private Scoreboard scoreboard;
    private Objective objective;
    private SidebarFrame lastFrame;

    private ManagedState(final Player player) {
      this.player = player;
    }

    private void render(final SidebarFrame frame) {
      if (frame.equals(lastFrame)) {
        return;
      }
      ensureScoreboard();
      if (!frame.title().equals(lastFrame == null ? null : lastFrame.title())) {
        objective.displayName(frame.title());
      }
      List<Component> lines = frame.lines();
      for (int index = 0; index < ENTRIES.length; index++) {
        String entry = ENTRIES[index];
        if (index < lines.size()) {
          Score score = objective.getScore(entry);
          score.customName(lines.get(index));
          score.setScore(SidebarFrame.MAXIMUM_LINES - index);
          score.numberFormat(NumberFormat.blank());
        } else {
          scoreboard.resetScores(entry);
        }
      }
      lastFrame = frame;
    }

    private void ensureScoreboard() {
      if (scoreboard != null) {
        return;
      }
      previous = player.getScoreboard();
      scoreboard = Objects.requireNonNull(Bukkit.getScoreboardManager(), "scoreboard manager")
          .getNewScoreboard();
      objective = scoreboard.registerNewObjective("vex_sidebar", Criteria.DUMMY, Component.empty());
      objective.setDisplaySlot(DisplaySlot.SIDEBAR);
      player.setScoreboard(scoreboard);
    }

    private synchronized void close() {
      if (scoreboard != null && player.isOnline() && player.getScoreboard() == scoreboard) {
        Scoreboard fallback = previous == null
            ? Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard()
            : previous;
        player.setScoreboard(fallback);
      }
      scoreboard = null;
      objective = null;
      lastFrame = null;
    }
  }
}
