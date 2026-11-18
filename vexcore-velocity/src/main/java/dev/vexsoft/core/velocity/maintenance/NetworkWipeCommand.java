package dev.vexsoft.core.velocity.maintenance;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.vexsoft.core.common.data.redis.RedisPlayerDataStore;
import dev.vexsoft.core.common.data.sql.SharedDatabase;
import dev.vexsoft.core.common.messaging.service.RedisMessageChannel;
import dev.vexsoft.core.velocity.VexCoreVelocityPlugin;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;

/** Console-only cutover for the single Velocity authority of a Redis/PostgreSQL network. */
public final class NetworkWipeCommand implements SimpleCommand, AutoCloseable {

    private static final List<String> WORLD_TABLES = List.of("vex_garden_admissions", "vex_garden_upgrades",
        "vex_garden_upgrade_backups", "vex_garden_worlds", "vex_player_gardens");
    private final VexCoreVelocityPlugin plugin;
    private final ProxyServer proxy;
    private final RedisPlayerDataStore live;
    private final RedisMessageChannel channel;
    private final SharedDatabase database;
    private final Set<Player> connecting = new HashSet<>();
    private CompletableFuture<?> active;
    private boolean restartRequired;

    public NetworkWipeCommand(final VexCoreVelocityPlugin plugin, final RedisPlayerDataStore live,
        final RedisMessageChannel channel, final SharedDatabase database) {
        this.plugin = plugin;
        proxy = plugin.getProxyServer();
        this.live = live;
        this.channel = channel;
        this.database = database;
    }

    public void start() {
        proxy.getEventManager().register(plugin, this);
        proxy.getCommandManager().register(proxy.getCommandManager().metaBuilder("vexwipe").plugin(plugin).build(), this);
    }

    @Override
    public boolean hasPermission(final Invocation invocation) {
        return invocation.source() == proxy.getConsoleCommandSource();
    }

    @Override
    public synchronized void execute(final Invocation invocation) {
        if (!hasPermission(invocation)) {
            return;
        }
        if (!List.of(invocation.arguments()).equals(List.of("confirm"))) {
            tell("Stoppe alle Paper-/Garden-Backends. vexwipe confirm löscht alle Spielerdaten und Gärten. "
                + "Veröffentlichte Konfigurationen bleiben erhalten.");
            return;
        }
        if (active != null || restartRequired) {
            tell("Wipe läuft oder wurde abgeschlossen. Nach Abschluss Velocity neu starten.");
            return;
        }
        if (!connecting.isEmpty() || proxy.getPlayerCount() != 0) {
            tell("Abgebrochen. Mindestens ein Spieler ist verbunden oder meldet sich gerade an.");
            return;
        }
        tell("Prüfe Netzwerk und Speicher für den Wipe.");
        active = channel.wipePlayerData(() -> database.transaction(NetworkWipeCommand::clearGardens).join());
        active.whenComplete((count, failure) -> finished(count, failure));
    }

    static Void clearGardens(final Connection connection) throws SQLException {
        try (var exists = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL");
             var clear = connection.createStatement()) {
            for (var table : WORLD_TABLES) {
                exists.setString(1, table);
                try (var row = exists.executeQuery()) {
                    if (row.next() && row.getBoolean(1)) {
                        clear.executeUpdate("DELETE FROM " + table);
                    }
                }
            }
        }
        return null;
    }

    private synchronized void finished(final Object count, final Throwable failure) {
        active = null;
        if (failure != null) {
            Throwable cause = failure;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (String.valueOf(cause.getMessage()).contains("BACKENDS_STILL_RUNNING")) {
                tell("Abgebrochen. Stoppe alle Backends, warte auf deren Abmeldung und wiederhole vexwipe confirm.");
            } else {
                plugin.getPlatformLogger().error("Network wipe failed", failure);
                tell("Wipe fehlgeschlagen. Backends gestoppt lassen, Fehler im Log prüfen und vexwipe confirm wiederholen.");
            }
            return;
        }
        restartRequired = true;
        tell("Wipe abgeschlossen. " + count + " Speichereinträge entfernt, Gärten gelöscht, PostgreSQL bestätigt. "
            + "Starte jetzt Velocity neu und anschließend die Backends.");
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public void login(final LoginEvent event) {
        if (!event.getResult().isAllowed()) {
            return;
        }
        synchronized (this) {
            if (active != null || restartRequired) {
                deny(event);
                return;
            }
            connecting.add(event.getPlayer());
        }
        try {
            if (!live.wipePending().join()) {
                return;
            }
        } catch (RuntimeException failure) {
            plugin.getPlatformLogger().warn("Cannot verify network maintenance state", failure);
        }
        synchronized (this) {
            connecting.remove(event.getPlayer());
            deny(event);
        }
    }

    @Subscribe
    public synchronized void joined(final PostLoginEvent event) {
        connecting.remove(event.getPlayer());
    }

    @Subscribe
    public synchronized void left(final DisconnectEvent event) {
        connecting.remove(event.getPlayer());
    }

    private void deny(final LoginEvent event) {
        event.setResult(ResultedEvent.ComponentResult.denied(Component.text("Das Netzwerk wird gewartet.")));
    }

    private void tell(final String message) {
        proxy.getConsoleCommandSource().sendMessage(Component.text("[VexCore] " + message));
    }

    @Override
    public void close() {
        CompletableFuture<?> drain;
        synchronized (this) {
            restartRequired = true;
            drain = active;
        }
        if (drain != null) {
            drain.handle((ignored, failure) -> null).join();
        }
        proxy.getCommandManager().unregister("vexwipe");
        proxy.getEventManager().unregisterListener(plugin, this);
    }
}
