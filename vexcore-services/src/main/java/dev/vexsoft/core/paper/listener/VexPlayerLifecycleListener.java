package dev.vexsoft.core.paper.listener;

import net.kyori.adventure.text.Component;
import dev.vexsoft.core.common.service.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.common.service.data.PlayerDataStoreService;
import dev.vexsoft.core.paper.service.signals.SignalService;
import dev.vexsoft.core.paper.signals.core.PlayerDataLoadedSignal;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.player.PlayerIdentityService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.Plugin;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Dependencies({
    PlayerDataCoordinatorService.class,
    PlayerDataStoreService.class,
    PlayerIdentityService.class,
    SignalService.class
})
public final class VexPlayerLifecycleListener implements Listener {

  private final PlayerDataCoordinatorService players;
  private final SignalService signals;
  private final PlayerIdentityService identities;
  private final PlayerDataStoreService dataStore;
  private final Logger logger;
  private final ConcurrentHashMap<UUID, UUID> loginAttempts = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, UUID> readyPlayers = new ConcurrentHashMap<>();

  public VexPlayerLifecycleListener(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    players = checkedServices.require(PlayerDataCoordinatorService.class);
    dataStore = checkedServices.require(PlayerDataStoreService.class);
    identities = checkedServices.require(PlayerIdentityService.class);
    signals = checkedServices.require(SignalService.class);
    if (!(checkedServices.getOwner() instanceof Plugin plugin)) {
      throw new IllegalArgumentException("Player lifecycle listener owner must be a Bukkit plugin");
    }
    logger = plugin.getLogger();
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onPlayerPreLogin(final AsyncPlayerPreLoginEvent event) {
    if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
      return;
    }
    UUID uniqueId = event.getUniqueId();
    UUID attemptId = UUID.randomUUID();
    loginAttempts.put(uniqueId, attemptId);
    CompletableFuture<VexPlayer> loading = identities.record(uniqueId, event.getName())
        .thenCompose(ignored -> players.load(uniqueId, event.getName()));
    try {
      loading.orTimeout(dataStore.getLoginTimeout().toMillis(), TimeUnit.MILLISECONDS).join();
      if (loginAttempts.get(uniqueId) != attemptId) {
        event.disallow(
            AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
            Component.text(dataStore.getLoginKickMessage())
        );
        return;
      }
      readyPlayers.put(uniqueId, attemptId);
      CompletableFuture.runAsync(
          () -> cleanupAttempt(uniqueId, attemptId),
          CompletableFuture.delayedExecutor(
              dataStore.getLoginTimeout().toMillis(),
              TimeUnit.MILLISECONDS
          )
      );
    } catch (RuntimeException exception) {
      cleanupAttempt(uniqueId, attemptId);
      loading.thenAccept(ignored -> cleanupAbandonedLoad(uniqueId, attemptId));
      logger.log(Level.SEVERE, "Unable to load VexPlayer " + uniqueId, exception);
      event.disallow(
          AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
          Component.text(dataStore.getLoginKickMessage())
      );
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerPreLoginComplete(final AsyncPlayerPreLoginEvent event) {
    if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
      cleanupCurrentAttempt(event.getUniqueId());
    }
  }

  @EventHandler
  public void onPlayerJoin(final PlayerJoinEvent event) {
    UUID uniqueId = event.getPlayer().getUniqueId();
    UUID attemptId = readyPlayers.remove(uniqueId);
    if (attemptId == null || !loginAttempts.remove(uniqueId, attemptId)) {
      event.getPlayer().kick(Component.text(dataStore.getLoginKickMessage()));
      return;
    }
    VexPlayer player = players.find(uniqueId).orElseThrow(
        () -> new IllegalStateException(
            "VexPlayer was not loaded before join: " + uniqueId
        )
    );
    player.bindPlatformPlayer(event.getPlayer());
    signals.publish(new PlayerDataLoadedSignal(player));
  }

  @EventHandler
  public void onPlayerQuit(final PlayerQuitEvent event) {
    cleanupCurrentAttempt(event.getPlayer().getUniqueId());
    players.find(event.getPlayer().getUniqueId()).ifPresent(VexPlayer::unbindPlatformPlayer);
    players.saveAndRemove(event.getPlayer().getUniqueId()).exceptionally(throwable -> {
      logger.log(Level.SEVERE, "Unable to save VexPlayer " + event.getPlayer().getUniqueId(), throwable);
      return null;
    });
  }

  private void cleanupCurrentAttempt(final UUID uniqueId) {
    UUID attemptId = loginAttempts.get(uniqueId);
    if (attemptId != null) {
      cleanupAttempt(uniqueId, attemptId);
    }
  }

  private void cleanupAttempt(final UUID uniqueId, final UUID attemptId) {
    if (!loginAttempts.remove(uniqueId, attemptId)) {
      return;
    }
    readyPlayers.remove(uniqueId, attemptId);
    players.remove(uniqueId);
  }

  private void cleanupAbandonedLoad(final UUID uniqueId, final UUID attemptId) {
    UUID currentAttempt = loginAttempts.get(uniqueId);
    if (currentAttempt == null || currentAttempt.equals(attemptId)) {
      cleanupAttempt(uniqueId, attemptId);
      if (loginAttempts.get(uniqueId) == null) {
        players.remove(uniqueId);
      }
    }
  }
}
