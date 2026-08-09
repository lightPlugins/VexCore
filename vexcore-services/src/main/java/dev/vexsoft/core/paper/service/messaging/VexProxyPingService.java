package dev.vexsoft.core.paper.service.messaging;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.service.messages.SendMessageService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.scheduler.VexTask;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Dependencies({ScheduleService.class, SendMessageService.class})
public final class VexProxyPingService implements ProxyPingService, AutoCloseable {

  private static final long TIMEOUT_TICKS = 100L;

  private final ScheduleService schedules;
  private final SendMessageService messages;
  private final Map<UUID, PendingPing> pending = new ConcurrentHashMap<>();

  public VexProxyPingService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    this.schedules = checkedServices.require(ScheduleService.class);
    this.messages = checkedServices.require(SendMessageService.class);
  }

  @Override
  public UUID begin(final Player player) {
    Player checkedPlayer = Objects.requireNonNull(player, "player");
    UUID requestId = UUID.randomUUID();
    PendingPing ping = new PendingPing(checkedPlayer.getUniqueId(), System.nanoTime());
    pending.put(requestId, ping);
    schedules.runForLater(
        checkedPlayer,
        TIMEOUT_TICKS,
        () -> timeout(requestId),
        () -> pending.remove(requestId)
    ).ifPresentOrElse(
        ping::setTimeoutTask,
        () -> pending.remove(requestId)
    );
    return requestId;
  }

  @Override
  public void cancel(final UUID requestId) {
    PendingPing ping = pending.remove(Objects.requireNonNull(requestId, "requestId"));
    if (ping != null) {
      ping.cancelTimeout();
    }
  }

  @Override
  public void complete(final UUID requestId) {
    PendingPing ping = pending.remove(Objects.requireNonNull(requestId, "requestId"));
    if (ping == null) {
      return;
    }
    ping.cancelTimeout();
    Player player = Bukkit.getPlayer(ping.getPlayerId());
    if (player == null) {
      return;
    }
    long latency = Math.max(0L, (System.nanoTime() - ping.getStartedAt()) / 1_000_000L);
    schedules.runFor(player, () -> messages.send(
        player,
        "commands.vexcore.debug.proxy.ping.success",
        true,
        Map.of("latency", Long.toString(latency))
    ));
  }

  @Override
  public void close() {
    for (PendingPing ping : new ArrayList<>(pending.values())) {
      ping.cancelTimeout();
    }
    pending.clear();
  }

  private void timeout(final UUID requestId) {
    PendingPing ping = pending.remove(requestId);
    if (ping == null) {
      return;
    }
    Player player = Bukkit.getPlayer(ping.getPlayerId());
    if (player != null) {
      messages.send(player, "commands.vexcore.debug.proxy.ping.timeout", true);
    }
  }

  private static final class PendingPing {

    private final UUID playerId;
    private final long startedAt;
    private volatile VexTask timeoutTask;

    private PendingPing(final UUID playerId, final long startedAt) {
      this.playerId = playerId;
      this.startedAt = startedAt;
    }

    private UUID getPlayerId() {
      return playerId;
    }

    private long getStartedAt() {
      return startedAt;
    }

    private void setTimeoutTask(final VexTask timeoutTask) {
      this.timeoutTask = timeoutTask;
    }

    private void cancelTimeout() {
      VexTask task = timeoutTask;
      if (task != null) {
        task.cancel();
      }
    }
  }
}
