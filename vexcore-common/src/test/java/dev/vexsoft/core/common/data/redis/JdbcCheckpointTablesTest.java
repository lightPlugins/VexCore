package dev.vexsoft.core.common.data.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Exercises both SQL layouts; PostgreSQL uses an isolated database per test and requires VEXCORE_TEST_POSTGRES. */
class JdbcCheckpointTablesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PLAYER = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String MEMBER = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String PROFILE = "cccccccc-cccc-cccc-cccc-cccccccccccc";
    private static final String DOMAIN_SUFFIX = "_0123456789abcdef0123456789abcdef";
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void splitRowsExposePlayerProfileDomainRevisionAndJson(final boolean postgres) throws Exception {
        try (var database = new Database(postgres)) {
            String inventory = personal("vexcore", "native_state" + DOMAIN_SUFFIX, PLAYER);
            String memberInventory = personal("vexcore", "native_state" + DOMAIN_SUFFIX, MEMBER);
            String roster = shared("vexisles", "roster");
            String membership = personal("vexisles", "membership", PLAYER);
            String loadouts = personal("vexisles", "loadouts", PLAYER);
            String shops = shared("vexisles", "shops");
            Map<String, String> values = Map.of(
                inventory, record("{\"items\":[\"stone\"]}", 7),
                memberInventory, record("{}", 2), roster, record("{\"name\":\"Coop\"}", 1),
                membership, record("{\"active\":true}", 3), loadouts, record("{}", 1), shops, record("{}", 4));
            assertTrue(database.publish(0, "1-0", values, Set.of()));
            assertState(values, database.repository.load("network").join().values());
            try (var connection = database.open(); var statement = connection.createStatement(); var rows =
                statement.executeQuery("SELECT scope, domain, player_uuid, profile_uuid, container_key, revision, value"
                    + " FROM " + database.table("vexcore", "inventories") + " ORDER BY player_uuid")) {
                assertTrue(rows.next());
                assertEquals("personal", rows.getString(1));
                assertEquals("vexisles:game", rows.getString(2));
                assertEquals(PLAYER, rows.getString(3));
                assertEquals(PROFILE, rows.getString(4));
                assertEquals("native_state" + DOMAIN_SUFFIX, rows.getString(5));
                assertEquals(7, rows.getLong(6));
                assertEquals("stone", MAPPER.readTree(rows.getString(7)).path("items").get(0).asText());
                assertTrue(rows.next());
                assertEquals(MEMBER, rows.getString(3));
                assertFalse(rows.next());
            }
            assertEquals(1, database.count("vexisles", "profiles"));
            assertEquals(1, database.count("vexisles", "profile_members"));
            assertEquals(1, database.count("vexisles", "loadouts"));
            assertEquals(1, database.count("vexisles", "shops"));
            if (postgres) {
                try (var connection = database.open(); var statement = connection.createStatement(); var rows =
                    statement.executeQuery("SELECT data_type FROM information_schema.columns WHERE table_schema ="
                        + " 'vexcore' AND table_name = 'inventories' AND column_name = 'value'")) {
                    assertTrue(rows.next());
                    assertEquals("jsonb", rows.getString(1));
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deltaLeavesUnchangedRowsUntouchedAndDeletesExplicitRecords(final boolean postgres) throws Exception {
        try (var database = new Database(postgres)) {
            String inventory = personal("vexcore", "native_state" + DOMAIN_SUFFIX, PLAYER);
            String money = personal("vexcore", "currencies" + DOMAIN_SUFFIX, PLAYER);
            String pet = personal("vexisles", "pet_buffer", PLAYER);
            assertTrue(database.publish(0, "1-0", Map.of(inventory, record("{}", 1),
                money, record("\"9007199254740993000\"", 1), pet, record("{}", 1)), Set.of()));
            assertTrue(database.publish(1, "2-0", Map.of(money, record("\"9007199254740993001\"", 2)), Set.of(pet)));
            assertEquals(1, database.generation("vexcore", "inventories"));
            assertEquals(2, database.generation("vexcore", "currencies"));
            assertEquals(0, database.count("vexisles", "pet_buffer"));
            assertState(Map.of(inventory, record("{}", 1), money, record("\"9007199254740993001\"", 2)),
                database.repository.load("network").join().values());
            assertEquals(new NetworkCheckpointHead(2, "2-0"), database.repository.head("network").join());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sqlFailureRollsBackAllTablesNewDdlAndCursor(final boolean postgres) throws Exception {
        try (var database = new Database(postgres)) {
            String inventory = personal("vexcore", "native_state" + DOMAIN_SUFFIX, PLAYER);
            String money = personal("vexcore", "currencies" + DOMAIN_SUFFIX, PLAYER);
            String shops = shared("vexisles", "shops");
            Map<String, String> original = Map.of(inventory, record("{}", 1), shops, record("{}", 1));
            assertTrue(database.publish(0, "1-0", original, Set.of()));
            try (var connection = database.open(); var statement = connection.createStatement()) {
                String table = database.table("vexisles", "shops");
                if (postgres) {
                    statement.executeUpdate("ALTER TABLE " + table
                        + " ADD CONSTRAINT reject_blocked CHECK (value->>'blocked' IS DISTINCT FROM 'true')");
                } else {
                    statement.executeUpdate("CREATE TRIGGER reject_blocked BEFORE UPDATE ON " + table
                        + " WHEN json_extract(new.value, '$.blocked') = 1 BEGIN SELECT RAISE(ABORT, 'blocked'); END");
                }
            }
            assertThrows(Exception.class, () -> database.publish(1, "2-0", Map.of(
                inventory, record("{\"items\":[]}", 2), money, record("100", 1),
                shops, record("{\"blocked\":true}", 2)), Set.of()));
            assertEquals(new NetworkCheckpointHead(1, "1-0"), database.repository.head("network").join());
            assertState(original, database.repository.load("network").join().values());
            assertEquals(2, database.count("vexcore", "checkpoint_tables"));
            assertFalse(database.exists("vexcore", "currencies"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void competingPublishersAndRecoveryEpochCannotMixRows(final boolean postgres) throws Exception {
        try (var database = new Database(postgres)) {
            String inventory = personal("vexcore", "native_state" + DOMAIN_SUFFIX, PLAYER);
            String money = personal("vexcore", "currencies" + DOMAIN_SUFFIX, PLAYER);
            Map<String, String> left = Map.of(inventory, record("{\"items\":[\"a\"]}", 1), money, record("10", 1));
            Map<String, String> right = Map.of(inventory, record("{\"items\":[\"b\"]}", 1), money, record("20", 1));
            var first = database.repository.publishChanges("network", new NetworkCheckpointChanges(0, "1-0", left, Set.of()), "");
            var second = database.repository.publishChanges("network", new NetworkCheckpointChanges(0, "2-0", right, Set.of()), "");
            boolean won = first.join();
            assertEquals(1, (won ? 1 : 0) + (second.join() ? 1 : 0));
            assertState(won ? left : right, database.repository.load("network").join().values());
            assertTrue(database.repository.activateEpoch("network", 1, "new-epoch").join());
            assertFalse(database.repository.publishChanges("network",
                new NetworkCheckpointChanges(2, "3-0", Map.of(), Set.of(inventory)), "old-epoch").join());
            assertState(won ? left : right, database.repository.load("network").join().values());
            assertThrows(Exception.class, () -> database.repository.verifyDirectMode().join());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recoveryReadsOneSnapshotDuringConcurrentPublication(final boolean postgres) throws Exception {
        try (var database = new Database(postgres)) {
            String inventory = personal("vexcore", "native_state" + DOMAIN_SUFFIX, PLAYER);
            String shops = shared("vexisles", "shops");
            Map<String, String> old = Map.of(inventory, record("{\"items\":[\"old\"]}", 1), shops, record("1", 1));
            Map<String, String> newer = Map.of(inventory, record("{\"items\":[\"new\"]}", 2), shops, record("2", 2));
            assertTrue(database.publish(0, "1-0", old, Set.of()));
            var reached = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var enabled = new AtomicBoolean();
            var reader = new JdbcNetworkCheckpoints(() -> gated(database.open(), enabled, reached, release), database.executor);
            reader.head("network").join();
            enabled.set(true);
            var loading = reader.load("network");
            try {
                assertTrue(reached.await(10, TimeUnit.SECONDS));
                assertTrue(database.publish(1, "2-0", newer, Set.of()));
            } finally {
                release.countDown();
            }
            var recovered = loading.get(10, TimeUnit.SECONDS);
            assertEquals("1-0", recovered.position());
            assertState(old, recovered.values());
            assertState(newer, database.repository.load("network").join().values());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mixedCaseOwnersKeepRecordKeysAcrossMigrationUpdatesAndDeletion(final boolean postgres) throws Exception {
        try (var database = new Database(postgres)) {
            String roster = shared("VexIsles", "roster");
            String membership = personal("VexIsles", "membership", PLAYER);
            String inventory = personal("VexCore", "native_state" + DOMAIN_SUFFIX, PLAYER);
            String account = "a|VexIsles|" + PLAYER + "|profiles";
            String global = "g|VexIsles|market";
            String lowerCaseGlobal = "g|vexisles|market";
            Map<String, String> original = Map.of(
                roster, record("{}", 4), membership, record("{}", 5), inventory, record("{}", 6),
                account, record("{}", 7), global, record("100", 8), lowerCaseGlobal, record("200", 9));
            database.legacy("mixed-case", 17, "123-4", "old-epoch", original);
            var migrated = database.repository.load("mixed-case").join();
            assertEquals(17, migrated.generation());
            assertState(original, migrated.values());
            assertEquals(1, database.count("vexisles", "profiles"));
            assertEquals(1, database.count("vexisles", "profile_members"));
            assertEquals(1, database.count("vexisles", "profile_accounts"));
            assertEquals(1, database.count("vexcore", "inventories"));
            assertEquals(2, database.count("vexisles", "market"));
            if (postgres) {
                assertFalse(database.exists("VexIsles", "market"));
            }
            Map<String, String> changed = Map.of(global, record("150", 10), inventory, record("{\"items\":[]}", 7));
            assertTrue(database.repository.publishChanges("mixed-case",
                new NetworkCheckpointChanges(17, "124-0", changed, Set.of(membership)), "old-epoch").join());
            var expected = new LinkedHashMap<>(original);
            expected.putAll(changed);
            expected.remove(membership);
            assertState(expected, database.repository.load("mixed-case").join().values());
            assertEquals(0, database.count("vexisles", "profile_members"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void importsPreRedisTablesOnceAndNormalizesIdentityTime(final boolean postgres) throws Exception {
        try (var database = new Database(postgres)) {
            long time = 1700000000123L;
            try (var connection = database.open(); var statement = connection.createStatement()) {
                String json = postgres ? "JSONB" : "TEXT";
                statement.executeUpdate("CREATE TABLE vex_test_players (player_id TEXT PRIMARY KEY,"
                    + " player_name TEXT, coins " + json + ")");
                statement.executeUpdate("CREATE TABLE vex_global_data (owner TEXT, data_key TEXT, value "
                    + json + ", revision BIGINT)");
                statement.executeUpdate("CREATE TABLE vex_profile_documents (address TEXT, owner TEXT, name TEXT, value "
                    + json + ", revision BIGINT)");
                statement.executeUpdate("CREATE TABLE vex_player_identities (player_id TEXT, player_name TEXT, updated_at "
                    + (postgres ? "TIMESTAMPTZ" : "BIGINT") + ")");
                statement.executeUpdate("INSERT INTO vex_test_players VALUES ('" + PLAYER + "', 'Legacy', '100')");
                statement.executeUpdate("INSERT INTO vex_global_data VALUES ('VexIsles', 'market', '{}', 5)");
                statement.executeUpdate("INSERT INTO vex_profile_documents VALUES ('profile/vexisles:game/" + PROFILE
                    + "/shared', 'VexIsles', 'roster', '{}', 8)");
                try (var insert = connection.prepareStatement("INSERT INTO vex_player_identities VALUES (?, ?, ?)")) {
                    insert.setString(1, PLAYER);
                    insert.setString(2, "Legacy");
                    if (postgres) {
                        insert.setTimestamp(3, Timestamp.from(Instant.ofEpochMilli(time)));
                    } else {
                        insert.setLong(3, time);
                    }
                    insert.executeUpdate();
                }
            }
            var imported = database.repository.load("legacy").join();
            assertEquals(5, imported.values().size());
            var identity = MAPPER.readTree(MAPPER.readTree(imported.values().get("i|" + PLAYER)).path("value").asText());
            assertEquals(Long.toString(time), identity.path("updatedAt").asText());
            assertEquals(1, database.count("vexcore", "players"));
            assertEquals(1, database.count("vexisles", "profiles"));
            assertTrue(imported.values().containsKey(shared("VexIsles", "roster")));
            assertTrue(imported.values().containsKey("g|VexIsles|market"));
            try (var connection = database.open(); var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE vex_test_players SET coins = '0'");
            }
            assertState(imported.values(), database.repository.load("legacy").join().values());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void migratesOldWholeNetworkBlobWithGenerationEpochAndPosition(final boolean postgres) throws Exception {
        try (var database = new Database(postgres)) {
            Map<String, String> values = Map.of(personal("vexcore", "native_state" + DOMAIN_SUFFIX, PLAYER),
                record("{}", 4), shared("vexisles", "roster"), record("{\"name\":\"Legacy\"}", 9),
                "n|legacy", record(PLAYER, 1), "i|" + PLAYER,
                record("{\"id\":\"" + PLAYER + "\",\"name\":\"Legacy\"}", 1));
            database.legacy("old-network", 17, "123-4", "old-epoch", values);
            var migrated = database.repository.load("old-network").join();
            assertEquals(17, migrated.generation());
            assertEquals("123-4", migrated.position());
            assertState(values, migrated.values());
            try (var connection = database.open(); var statement = connection.createStatement(); var rows =
                statement.executeQuery("SELECT COUNT(*) FROM vex_network_checkpoints")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
            assertFalse(database.repository.publishChanges("old-network",
                new NetworkCheckpointChanges(17, "124-0", Map.of(), Set.of()), "wrong-epoch").join());
            assertTrue(database.repository.publishChanges("old-network",
                new NetworkCheckpointChanges(17, "124-0", Map.of(), Set.of()), "old-epoch").join());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedMigrationRetainsOldSourceAndDoesNotExposePartialNamespace(final boolean postgres) throws Exception {
        try (var database = new Database(postgres)) {
            database.legacy("old-network", 3, "8-0", "epoch", Map.of(
                personal("vexcore", "native_state" + DOMAIN_SUFFIX, PLAYER), record("{}", 1),
                shared("vexisles", "shops"), record("invalid-json", 1)));
            assertThrows(Exception.class, () -> database.repository.load("old-network").join());
            try (var connection = database.open(); var statement = connection.createStatement(); var rows =
                statement.executeQuery("SELECT COUNT(*) FROM vex_network_checkpoints WHERE namespace = 'old-network'")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
            assertFalse(database.exists("vexcore", "inventories"));
            assertEquals(1, database.count("vexcore", "checkpoints"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void isolatesNamespacesAndGroupsDynamicReceiptsWithoutTablePerOperation(final boolean postgres) throws Exception {
        try (var database = new Database(postgres)) {
            String first = shared("vexisles", "departure" + DOMAIN_SUFFIX);
            String second = shared("vexisles", "departure_ffffffffffffffffffffffffffffffff");
            String future = shared("futureplugin", "island_access");
            assertTrue(database.publish(0, "1-0", Map.of(first, record("{}", 1), second, record("{}", 1),
                future, record("{}", 1)), Set.of()));
            assertEquals(2, database.count("vexisles", "profile_departures"));
            assertEquals(1, database.count("futureplugin", "island_access"));
            assertEquals(2, database.count("vexcore", "checkpoint_tables"));
            assertTrue(database.repository.load("other-network").join().values().isEmpty());
            assertTrue(database.repository.publishChanges("other-network",
                new NetworkCheckpointChanges(0, "9-0", Map.of(first, record("{\"other\":true}", 1)), Set.of()), "").join());
            assertEquals(3, database.count("vexisles", "profile_departures"));
            assertEquals(3, database.repository.load("network").join().values().size());
            assertEquals(1, database.repository.load("other-network").join().values().size());
        }
    }

    private static Connection gated(
        final Connection connection, final AtomicBoolean enabled, final CountDownLatch reached, final CountDownLatch release
    ) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
            (proxy, method, arguments) -> {
                try {
                    Object result = method.invoke(connection, arguments);
                    if (method.getName().equals("prepareStatement") && enabled.get()
                        && ((String) arguments[0]).startsWith("SELECT record_key")) {
                        var statement = (PreparedStatement) result;
                        return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                            new Class<?>[]{PreparedStatement.class}, (statementProxy, statementMethod, statementArguments) -> {
                                try {
                                    if (statementMethod.getName().equals("executeQuery") && enabled.compareAndSet(true, false)) {
                                        reached.countDown();
                                        assertTrue(release.await(15, TimeUnit.SECONDS));
                                    }
                                    return statementMethod.invoke(statement, statementArguments);
                                } catch (InvocationTargetException failure) {
                                    throw failure.getCause();
                                }
                            });
                    }
                    return result;
                } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                }
            });
    }

    private static String personal(final String owner, final String container, final String player) {
        return "p|profile/vexisles:game/" + PROFILE + '/' + player + '|' + owner + '|' + container;
    }

    private static String shared(final String owner, final String container) {
        return "p|profile/vexisles:game/" + PROFILE + "/shared|" + owner + '|' + container;
    }

    private static String record(final String value, final long revision) throws Exception {
        return MAPPER.writeValueAsString(Map.of("value", value, "revision", revision));
    }

    private static void assertState(final Map<String, String> expected, final Map<String, String> actual) throws Exception {
        assertEquals(expected.keySet(), actual.keySet());
        for (String key : expected.keySet()) {
            var left = MAPPER.readTree(expected.get(key));
            var right = MAPPER.readTree(actual.get(key));
            assertEquals(left.path("revision"), right.path("revision"));
            if (key.startsWith("n|")) {
                assertEquals(left.path("value"), right.path("value"));
            } else {
                assertEquals(MAPPER.readTree(left.path("value").asText()), MAPPER.readTree(right.path("value").asText()));
            }
        }
    }

    private final class Database implements AutoCloseable {

        private final boolean postgres;
        private final String url;
        private final String administrativeUrl;
        private final String databaseName;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final JdbcNetworkCheckpoints repository;

        private Database(final boolean postgres) throws Exception {
            this.postgres = postgres;
            databaseName = "checkpoint_test_" + UUID.randomUUID().toString().replace("-", "");
            administrativeUrl = postgres ? System.getenv("VEXCORE_TEST_POSTGRES") : null;
            if (postgres) {
                assumeTrue(administrativeUrl != null && !administrativeUrl.isBlank(), "Set VEXCORE_TEST_POSTGRES");
                String[] parts = administrativeUrl.split("\\?", 2);
                url = parts[0].substring(0, parts[0].lastIndexOf('/') + 1) + databaseName
                    + (parts.length == 2 ? '?' + parts[1] : "");
                try (var connection = DriverManager.getConnection(administrativeUrl); var statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE DATABASE " + databaseName);
                }
            } else {
                url = "jdbc:sqlite:" + directory.resolve(databaseName + ".db");
            }
            repository = new JdbcNetworkCheckpoints(this::open, executor);
            repository.load("network").join();
        }

        private Connection open() throws SQLException {
            var connection = DriverManager.getConnection(url);
            if (!postgres) {
                try (var statement = connection.createStatement()) {
                    statement.execute("PRAGMA journal_mode=WAL");
                    statement.execute("PRAGMA busy_timeout=10000");
                }
            }
            return connection;
        }

        private String table(final String owner, final String name) {
            return postgres ? '"' + owner + "\".\"" + name + '"' : '"' + owner + "__" + name + '"';
        }

        private boolean publish(
            final long generation, final String position, final Map<String, String> values, final Set<String> removed
        ) {
            return repository.publishChanges("network", new NetworkCheckpointChanges(generation, position, values, removed), "").join();
        }

        private int count(final String owner, final String name) throws Exception {
            try (var connection = open(); var statement = connection.createStatement(); var rows =
                statement.executeQuery("SELECT COUNT(*) FROM " + table(owner, name))) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }

        private long generation(final String owner, final String name) throws Exception {
            try (var connection = open(); var statement = connection.createStatement(); var rows =
                statement.executeQuery("SELECT updated_checkpoint FROM " + table(owner, name))) {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }

        private boolean exists(final String owner, final String name) throws Exception {
            try (var connection = open(); var tables = connection.getMetaData().getTables(null, postgres ? owner : null,
                postgres ? name : owner + "__" + name, new String[]{"TABLE"})) {
                return tables.next();
            }
        }

        private void legacy(
            final String namespace, final long generation, final String position, final String epoch, final Map<String, String> values
        ) throws Exception {
            try (var connection = open(); var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE vex_network_checkpoints (namespace VARCHAR(256) PRIMARY KEY,"
                    + " generation BIGINT NOT NULL, position VARCHAR(64) NOT NULL, live_epoch VARCHAR(64) NOT NULL, snapshot TEXT NOT NULL)");
                try (var insert = connection.prepareStatement("INSERT INTO vex_network_checkpoints VALUES (?, ?, ?, ?, ?)")) {
                    insert.setString(1, namespace);
                    insert.setLong(2, generation);
                    insert.setString(3, position);
                    insert.setString(4, epoch);
                    insert.setString(5, MAPPER.writeValueAsString(values));
                    insert.executeUpdate();
                }
            }
        }

        @Override
        public void close() throws Exception {
            executor.close();
            if (postgres) {
                try (var connection = DriverManager.getConnection(administrativeUrl); var statement = connection.createStatement()) {
                    statement.executeUpdate("DROP DATABASE " + databaseName + " WITH (FORCE)");
                }
            }
        }
    }
}
