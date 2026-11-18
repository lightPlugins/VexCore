package dev.vexsoft.core.velocity.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.vexsoft.core.common.data.PostgresPlayerDataStore;
import dev.vexsoft.core.common.data.redis.RedisPlayerDataStore;
import dev.vexsoft.core.common.data.redis.RedisSettings;
import dev.vexsoft.core.velocity.VexCoreVelocityPlugin;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

final class NetworkWipeCommandTest {

    @Test
    void rejectsNonConsoleAndOccupiedProxyBeforeTouchingStorage() {
        var messages = new ArrayList<String>();
        var console = proxy(ConsoleCommandSource.class, (self, method, args) -> {
            messages.add(((TextComponent) args[0]).content());
            return null;
        });
        var server = proxy(ProxyServer.class, (self, method, args) -> switch (method.getName()) {
            case "getConsoleCommandSource" -> console;
            case "getPlayerCount" -> 1;
            default -> throw new AssertionError(method.getName());
        });
        var plugin = new VexCoreVelocityPlugin(server, proxy(PluginContainer.class, (s, m, a) -> null),
            Path.of("test"), proxy(Logger.class, (s, m, a) -> null));
        var command = new NetworkWipeCommand(plugin, null, null, null);
        var player = proxy(CommandSource.class, (s, m, a) -> null);
        assertFalse(command.hasPermission(invocation(player)));
        command.execute(invocation(player));
        assertTrue(messages.isEmpty());
        command.execute(invocation(console));
        assertTrue(messages.getFirst().contains("Spieler"));
    }

    @Test
    void interruptedWipeResumesAndOnlyConfigurationSurvivesPostgresRecovery() throws Exception {
        String endpoint = System.getenv("VEXCORE_TEST_REDIS");
        assumeTrue(endpoint != null && !endpoint.isBlank());
        var namespace = "vexcore:test:" + UUID.randomUUID() + ":";
        var settings = new RedisSettings(RedisURI.create(endpoint), namespace, "wipe-test", 128, 1000, 300,
            Duration.ofSeconds(3));
        var inspector = RedisClient.create(endpoint);
        try (var connection = inspector.connect(); var postgres = EmbeddedPostgres.builder().start()) {
            var sql = new PostgresPlayerDataStore(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "",
                3, false, "postgres");
            try (var live = new RedisPlayerDataStore(sql, settings)) {
                live.readiness().join();
                var database = sql.sharedDatabase();
                var keys = connection.sync();
                String nodes = live.namespace() + "nodes:" + live.liveEpoch() + ":";
                keys.set(nodes + "proxy", "owner");
                keys.set(nodes + "server:lobby", "backend");
                var id = UUID.randomUUID();
                live.recordPlayerIdentity(id, "Alex").join();
                live.save("VexIsles", id, "Alex", Map.of("xp", "42")).join();
                live.setGlobalData("VexIsles", "published_gardens", "{\"template\":1}").join();
                live.setGlobalData("VexIsles", "shop_stock", "{}").join();
                database.transaction(db -> {
                    try (var create = db.createStatement()) {
                        for (var table : List.of("vex_garden_worlds", "vex_garden_templates")) {
                            create.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY)");
                            create.execute("INSERT INTO " + table + " VALUES (1)");
                        }
                    }
                    return null;
                }).join();
                Runnable worlds = () -> database.transaction(NetworkWipeCommand::clearGardens).join();
                assertThrows(CompletionException.class, () -> live.wipePlayerData("owner", worlds).join());
                assertFalse(live.wipePending().join());
                assertTrue(live.findPlayerIdentity(id).join().isPresent());
                keys.del(nodes + "server:lobby");
                assertThrows(CompletionException.class, () -> live.wipePlayerData("wrong-proxy", worlds).join());
                assertThrows(CompletionException.class, () -> live.wipePlayerData("owner", () -> {
                    throw new IllegalStateException("simulated world cleanup failure");
                }).join());
                assertTrue(live.wipePending().join());
                assertThrows(CompletionException.class, () -> live.recordPlayerIdentity(id, "Alex").join());
                live.wipePlayerData("owner", worlds).join();
                assertFalse(live.wipePending().join());
                assertTrue(live.findPlayerIdentity(id).join().isEmpty());
                assertEquals("{\"template\":1}", live.loadGlobalData("VexIsles", "published_gardens").join()
                    .orElseThrow().value());
                assertEquals(List.of("g|VexIsles|published_gardens"),
                    List.copyOf(sql.networkCheckpoints().load(namespace).join().values().keySet()));
                assertTrue(database.transaction(db -> {
                    try (var query = db.createStatement(); var row = query.executeQuery(
                        "SELECT (SELECT count(*) FROM vex_garden_worlds)=0 AND (SELECT count(*) FROM vex_garden_templates)=1")) {
                        return row.next() && row.getBoolean(1);
                    }
                }).join());
            } finally {
                var keys = connection.sync().keys(namespace + "*");
                if (!keys.isEmpty()) {
                    connection.sync().del(keys.toArray(String[]::new));
                }
            }
        } finally {
            inspector.shutdown();
        }
    }

    private static SimpleCommand.Invocation invocation(final CommandSource source) {
        return proxy(SimpleCommand.Invocation.class, (self, method, args) -> switch (method.getName()) {
            case "source" -> source;
            case "arguments" -> new String[]{"confirm"};
            default -> throw new AssertionError(method.getName());
        });
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
