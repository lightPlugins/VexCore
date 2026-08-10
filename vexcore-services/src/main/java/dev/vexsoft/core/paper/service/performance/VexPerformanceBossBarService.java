package dev.vexsoft.core.paper.service.performance;

import dev.vexsoft.core.paper.performance.PerformanceState;
import dev.vexsoft.core.paper.performance.ServerPerformanceSnapshot;

import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.scheduler.VexTask;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Dependencies({
    ServerPerformanceService.class,
    ScheduleService.class,
    LocalizationService.class,
    PlayerService.class
})
public final class VexPerformanceBossBarService implements
    PerformanceBossBarService,
    AutoCloseable {

  private static final long UPDATE_TICKS = 5L;
  private static final int PING_UPDATE_INTERVALS = 8;
  private static final double FULL_BAR_MSPT = 50.0D;

  private final ServerPerformanceService performance;
  private final ScheduleService schedules;
  private final LocalizationService localization;
  private final PlayerService players;
  private final Map<UUID, BossBar> viewers = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> cachedPings = new ConcurrentHashMap<>();
  private final AtomicBoolean started = new AtomicBoolean();
  private VexTask updateTask;
  private int updatesUntilPingRefresh;

  public VexPerformanceBossBarService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    this.performance = checkedServices.require(ServerPerformanceService.class);
    this.schedules = checkedServices.require(ScheduleService.class);
    this.localization = checkedServices.require(LocalizationService.class);
    this.players = checkedServices.require(PlayerService.class);
  }

  @Override
  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }
    updateTask = schedules.runGlobalTimer(UPDATE_TICKS, UPDATE_TICKS, this::updateAll);
  }

  @Override
  public void show(final Player player) {
    Player checkedPlayer = Objects.requireNonNull(player, "player");
    if (viewers.containsKey(checkedPlayer.getUniqueId())) {
      return;
    }
    BossBar bossBar = BossBar.bossBar(
        Component.empty(),
        0.0F,
        BossBar.Color.GREEN,
        BossBar.Overlay.PROGRESS
    );
    viewers.put(checkedPlayer.getUniqueId(), bossBar);
    update(checkedPlayer, bossBar, true);
    checkedPlayer.showBossBar(bossBar);
  }

  @Override
  public void hide(final Player player) {
    Player checkedPlayer = Objects.requireNonNull(player, "player");
    BossBar bossBar = viewers.remove(checkedPlayer.getUniqueId());
    cachedPings.remove(checkedPlayer.getUniqueId());
    if (bossBar != null) {
      checkedPlayer.hideBossBar(bossBar);
    }
  }

  @Override
  public boolean toggle(final Player player) {
    Player checkedPlayer = Objects.requireNonNull(player, "player");
    if (isVisible(checkedPlayer.getUniqueId())) {
      hide(checkedPlayer);
      return false;
    }
    show(checkedPlayer);
    return true;
  }

  @Override
  public boolean isVisible(final UUID playerId) {
    return viewers.containsKey(Objects.requireNonNull(playerId, "playerId"));
  }

  @Override
  public void close() {
    if (updateTask != null) {
      updateTask.cancel();
      updateTask = null;
    }
    for (Map.Entry<UUID, BossBar> viewer : viewers.entrySet()) {
      Player player = Bukkit.getPlayer(viewer.getKey());
      if (player != null) {
        player.hideBossBar(viewer.getValue());
      }
    }
    viewers.clear();
    cachedPings.clear();
    updatesUntilPingRefresh = 0;
    started.set(false);
  }

  private void updateAll() {
    boolean refreshPing = updatesUntilPingRefresh == 0;
    updatesUntilPingRefresh = refreshPing
        ? PING_UPDATE_INTERVALS - 1
        : updatesUntilPingRefresh - 1;
    for (Map.Entry<UUID, BossBar> viewer : viewers.entrySet()) {
      Player player = Bukkit.getPlayer(viewer.getKey());
      if (player == null) {
        viewers.remove(viewer.getKey(), viewer.getValue());
        cachedPings.remove(viewer.getKey());
        continue;
      }
      schedules.runFor(
          player,
          () -> update(player, viewer.getValue(), refreshPing),
          () -> {
            viewers.remove(viewer.getKey(), viewer.getValue());
            cachedPings.remove(viewer.getKey());
          }
      );
    }
  }

  private void update(
      final Player player,
      final BossBar bossBar,
      final boolean refreshPing
  ) {
    VexPlayer vexPlayer = players.find(player.getUniqueId()).orElse(null);
    if (vexPlayer == null) {
      return;
    }
    ServerPerformanceSnapshot snapshot = performance.getSnapshot();
    PerformanceState state = snapshot.getState();
    LocalizedMessage title = localization.resolve(
        vexPlayer.getContainer(LanguageContainer.class).getLanguage().getKey(),
        localizationKey(state),
        replacements(player, snapshot, refreshPing)
    );
    bossBar.name(title.getComponent());
    bossBar.color(color(state));
    bossBar.progress(progress(snapshot));
  }

  private Map<String, String> replacements(
      final Player player,
      final ServerPerformanceSnapshot snapshot,
      final boolean refreshPing
  ) {
    String ping = Integer.toString(ping(player, refreshPing));
    if (!snapshot.isAvailable()) {
      return Map.of(
          "tps", "N/A",
          "mspt", "N/A",
          "ping", ping
      );
    }
    return Map.of(
        "tps", format(snapshot.getCurrentTps()),
        "mspt", format(snapshot.getAverageMspt()),
        "ping", ping
    );
  }

  private int ping(final Player player, final boolean refresh) {
    UUID playerId = player.getUniqueId();
    Integer cached = cachedPings.get(playerId);
    if (refresh || cached == null) {
      int current = player.getPing();
      cachedPings.put(playerId, current);
      return current;
    }
    return cached;
  }

  private String localizationKey(final PerformanceState state) {
    return switch (state) {
      case GOOD -> "general.performance.bossbar.good";
      case MODERATE -> "general.performance.bossbar.moderate";
      case CRITICAL -> "general.performance.bossbar.critical";
      case UNAVAILABLE -> "general.performance.bossbar.unavailable";
    };
  }

  private BossBar.Color color(final PerformanceState state) {
    return switch (state) {
      case GOOD -> BossBar.Color.GREEN;
      case MODERATE -> BossBar.Color.YELLOW;
      case CRITICAL -> BossBar.Color.RED;
      case UNAVAILABLE -> BossBar.Color.BLUE;
    };
  }

  private float progress(final ServerPerformanceSnapshot snapshot) {
    if (!snapshot.isAvailable()) {
      return 0.0F;
    }
    return (float) Math.clamp(snapshot.getAverageMspt() / FULL_BAR_MSPT, 0.0D, 1.0D);
  }

  private String format(final double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }
}
