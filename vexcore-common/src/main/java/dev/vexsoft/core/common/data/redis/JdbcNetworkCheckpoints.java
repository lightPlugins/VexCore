package dev.vexsoft.core.common.data.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.common.data.profile.JdbcProfileDocuments;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;

/** Publishes changed container rows and their common recovery cursor in one fenced SQL transaction. */
@RequiredArgsConstructor
public final class JdbcNetworkCheckpoints {

    private final JdbcProfileDocuments.Connections connections;
    private final Executor executor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcCheckpointRecords records = new JdbcCheckpointRecords();
    private boolean initialized;

    /** Reads every container at one SQL snapshot, migrating old storage only for a new namespace. */
    public CompletableFuture<NetworkCheckpoint> load(final String namespace) {
        return transaction(connection -> {
            ensureNamespace(connection, namespace);
            var head = readHead(connection, namespace);
            return new NetworkCheckpoint(head.generation(), head.position(), records.read(connection, namespace));
        }, true);
    }

    /** Reads only the publication cursor; regular checkpoints never reload the network payload. */
    public CompletableFuture<NetworkCheckpointHead> head(final String namespace) {
        return transaction(connection -> {
            ensureNamespace(connection, namespace);
            return readHead(connection, namespace);
        }, true);
    }

    /** Replaces a complete checkpoint, primarily for migration tooling; live publishers use journal changes. */
    public CompletableFuture<Boolean> publish(final String namespace, final NetworkCheckpoint checkpoint) {
        return publish(namespace, checkpoint, "");
    }

    /** Publishes a complete replacement only if its generation and recovery epoch are still current. */
    public CompletableFuture<Boolean> publish(
        final String namespace, final NetworkCheckpoint checkpoint, final String epoch
    ) {
        return transaction(connection -> {
            if (!advance(connection, namespace, checkpoint.generation(), checkpoint.position(), epoch)) {
                return false;
            }
            Set<String> removed = new TreeSet<>(records.read(connection, namespace).keySet());
            removed.removeAll(checkpoint.values().keySet());
            records.write(connection, namespace, checkpoint.generation() + 1, checkpoint.values(), removed);
            return true;
        }, false);
    }

    /** Writes only final changed rows and deletions, committing the cursor after all tables succeed. */
    public CompletableFuture<Boolean> publishChanges(
        final String namespace, final NetworkCheckpointChanges changes, final String epoch
    ) {
        return transaction(connection -> {
            // This row lock serializes all writers for the namespace, including cold recovery claims.
            if (!advance(connection, namespace, changes.generation(), changes.position(), epoch)) {
                return false;
            }
            records.write(connection, namespace, changes.generation() + 1, changes.values(), changes.removed());
            return true;
        }, false);
    }

    /** Claims a new recovery epoch before exposing Redis state and invalidates prior SQL publishers. */
    public CompletableFuture<Boolean> activateEpoch(
        final String namespace, final long generation, final String epoch
    ) {
        return transaction(connection -> {
            try (var update = connection.prepareStatement("UPDATE " + metadata(connection)
                + " SET generation = generation + 1, live_epoch = ? WHERE namespace = ? AND generation = ?")) {
                update.setString(1, epoch);
                update.setString(2, namespace);
                update.setLong(3, generation);
                return update.executeUpdate() == 1;
            }
        }, false);
    }

    /** Prevents a direct-SQL instance from loading obsolete pre-Redis tables. */
    public CompletableFuture<Void> verifyDirectMode() {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = connections.open()) {
                if (hasTable(connection, "vex_network_checkpoints", null)) {
                    rejectPopulated(connection, "vex_network_checkpoints");
                }
                boolean postgres = JdbcCheckpointRecords.postgres(connection);
                if (hasTable(connection, postgres ? "checkpoints" : "vexcore__checkpoints", postgres ? "vexcore" : null)) {
                    rejectPopulated(connection, metadata(connection));
                }
            } catch (SQLException failure) {
                throw new IllegalStateException("Unable to validate legacy storage mode", failure);
            }
        }, executor);
    }

    private <T> CompletableFuture<T> transaction(final SqlWork<T> work, final boolean snapshot) {
        return CompletableFuture.supplyAsync(() -> {
            for (int attempt = 0; attempt < 3; attempt++) {
                try (Connection connection = connections.open()) {
                    ensureSchema(connection);
                    if (JdbcCheckpointRecords.postgres(connection)) {
                        connection.setTransactionIsolation(snapshot ? Connection.TRANSACTION_REPEATABLE_READ
                            : Connection.TRANSACTION_READ_COMMITTED);
                    }
                    connection.setAutoCommit(false);
                    try {
                        T result = work.run(connection);
                        connection.commit();
                        return result;
                    } catch (Exception failure) {
                        connection.rollback();
                        throw failure;
                    }
                } catch (Exception failure) {
                    if (failure instanceof SQLException sql && attempt < 2
                        && ("40001".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState())
                            || sql.getErrorCode() == 5)) {
                        continue;
                    }
                    throw new IllegalStateException("Unable to access the network checkpoint tables", failure);
                }
            }
            throw new IllegalStateException("Checkpoint transaction retries exhausted");
        }, executor);
    }

    private synchronized void ensureSchema(final Connection connection) throws SQLException {
        if (initialized) {
            return;
        }
        connection.setAutoCommit(false);
        try {
            JdbcCheckpointRecords.lockSchema(connection);
            try (var statement = connection.createStatement()) {
                if (JdbcCheckpointRecords.postgres(connection)) {
                    statement.executeUpdate("CREATE SCHEMA IF NOT EXISTS vexcore");
                }
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + metadata(connection)
                    + " (namespace VARCHAR(256) PRIMARY KEY, generation BIGINT NOT NULL, position VARCHAR(64) NOT NULL,"
                    + " live_epoch VARCHAR(64) NOT NULL, layout_version INTEGER NOT NULL DEFAULT 2)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS "
                    + JdbcCheckpointRecords.table(connection, "vexcore", "checkpoint_tables")
                    + " (namespace VARCHAR(256) NOT NULL, owner VARCHAR(63) NOT NULL, table_name VARCHAR(63) NOT NULL,"
                    + " PRIMARY KEY (namespace, owner, table_name))");
            }
            connection.commit();
            initialized = true;
        } catch (SQLException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void ensureNamespace(final Connection connection, final String namespace) throws Exception {
        if (readHead(connection, namespace) != null) {
            return;
        }
        try (var insert = connection.prepareStatement("INSERT INTO " + metadata(connection)
            + " (namespace, generation, position, live_epoch) VALUES (?, 0, '0-0', '') ON CONFLICT DO NOTHING")) {
            insert.setString(1, namespace);
            if (insert.executeUpdate() == 0) {
                return;
            }
        }
        long generation = 0;
        String position = "0-0";
        String epoch = "";
        Map<String, String> values = null;
        if (hasTable(connection, "vex_network_checkpoints", null)) {
            try (var select = connection.prepareStatement("SELECT generation, position, live_epoch, snapshot"
                + " FROM vex_network_checkpoints WHERE namespace = ?")) {
                select.setString(1, namespace);
                try (var rows = select.executeQuery()) {
                    if (rows.next()) {
                        generation = rows.getLong(1);
                        position = rows.getString(2);
                        epoch = rows.getString(3);
                        values = mapper.readValue(rows.getString(4), new TypeReference<Map<String, String>>() {
                        });
                    }
                }
            }
        }
        // Old versions must be stopped. Once migrated, legacy tables are never used for individual fallback reads.
        records.write(connection, namespace, generation, values == null ? readLegacy(connection) : values, Set.of());
        try (var update = connection.prepareStatement("UPDATE " + metadata(connection)
            + " SET generation = ?, position = ?, live_epoch = ? WHERE namespace = ?")) {
            update.setLong(1, generation);
            update.setString(2, position);
            update.setString(3, epoch);
            update.setString(4, namespace);
            update.executeUpdate();
        }
        if (values != null) {
            try (var delete = connection.prepareStatement("DELETE FROM vex_network_checkpoints WHERE namespace = ?")) {
                delete.setString(1, namespace);
                delete.executeUpdate();
            }
        }
    }

    private NetworkCheckpointHead readHead(final Connection connection, final String namespace) throws SQLException {
        try (var select = connection.prepareStatement("SELECT generation, position, layout_version FROM "
            + metadata(connection) + " WHERE namespace = ?")) {
            select.setString(1, namespace);
            try (var rows = select.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                if (rows.getInt(3) != 2) {
                    throw new IllegalStateException("Unsupported SQL checkpoint layout");
                }
                return new NetworkCheckpointHead(rows.getLong(1), rows.getString(2));
            }
        }
    }

    private boolean advance(
        final Connection connection, final String namespace, final long generation, final String position, final String epoch
    ) throws SQLException {
        try (var update = connection.prepareStatement("UPDATE " + metadata(connection)
            + " SET generation = generation + 1, position = ? WHERE namespace = ? AND generation = ? AND live_epoch = ?")) {
            update.setString(1, position);
            update.setString(2, namespace);
            update.setLong(3, generation);
            update.setString(4, epoch);
            return update.executeUpdate() == 1;
        }
    }

    private static String metadata(final Connection connection) throws SQLException {
        return JdbcCheckpointRecords.table(connection, "vexcore", "checkpoints");
    }

    private static boolean hasTable(final Connection connection, final String name, final String schema) throws SQLException {
        try (var tables = connection.getMetaData().getTables(null, schema, name, new String[]{"TABLE"})) {
            while (tables.next()) {
                if (tables.getString("TABLE_NAME").equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void rejectPopulated(final Connection connection, final String table) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
            "SELECT namespace FROM " + table + " LIMIT 1")) {
            if (rows.next()) {
                throw new IllegalStateException("This SQL database has Redis checkpoints. Enable Redis on every instance;"
                    + " switching back to legacy tables would load obsolete player state.");
            }
        }
    }

    private Map<String, String> readLegacy(final Connection connection) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        try (var tables = connection.getMetaData().getTables(null, null, "vex_%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String table = tables.getString("TABLE_NAME");
                if (!table.matches("vex_[a-z0-9_]+")) {
                    continue;
                }
                if (table.equals("vex_profile_documents")) {
                    readProfiles(connection, result);
                } else if (table.equals("vex_global_data")) {
                    readGlobals(connection, result);
                } else if (table.equals("vex_player_identities")) {
                    readIdentities(connection, result);
                } else if (table.endsWith("_players")) {
                    readAccounts(connection, table, result);
                }
            }
        }
        return result;
    }

    private void readProfiles(final Connection connection, final Map<String, String> target) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
            "SELECT address, owner, name, value, revision FROM vex_profile_documents WHERE revision > 0")) {
            while (rows.next()) {
                target.put("p|" + rows.getString(1) + '|' + rows.getString(2) + '|' + rows.getString(3),
                    record(rows.getString(4), rows.getLong(5)));
            }
        }
    }

    private void readGlobals(final Connection connection, final Map<String, String> target) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
            "SELECT owner, data_key, value, revision FROM vex_global_data")) {
            while (rows.next()) {
                target.put("g|" + rows.getString(1) + '|' + rows.getString(2), record(rows.getString(3), rows.getLong(4)));
            }
        }
    }

    private void readIdentities(final Connection connection, final Map<String, String> target) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
            "SELECT player_id, player_name, updated_at FROM vex_player_identities")) {
            while (rows.next()) {
                String id = rows.getString(1);
                String name = rows.getString(2);
                long updatedAt = JdbcCheckpointRecords.postgres(connection)
                    ? rows.getTimestamp(3).toInstant().toEpochMilli() : rows.getLong(3);
                String identity = mapper.writeValueAsString(Map.of("id", id, "name", name,
                    "updatedAt", Long.toString(updatedAt)));
                target.put("i|" + id, record(identity, 1));
                target.put("n|" + name.toLowerCase(Locale.ROOT), record(id, 1));
            }
        }
    }

    private void readAccounts(
        final Connection connection, final String table, final Map<String, String> target
    ) throws Exception {
        String owner = table.substring(4, table.length() - "_players".length());
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM \"" + table + "\"")) {
            var columns = rows.getMetaData();
            while (rows.next()) {
                String id = rows.getString("player_id");
                for (int index = 1; index <= columns.getColumnCount(); index++) {
                    String key = columns.getColumnName(index);
                    if (!key.equals("player_id") && !key.equals("player_name") && rows.getString(index) != null) {
                        target.put("a|" + owner + '|' + id + '|' + key, record(rows.getString(index), 1));
                    }
                }
            }
        }
    }

    private String record(final String value, final long revision) throws Exception {
        return mapper.writeValueAsString(Map.of("value", value, "revision", revision));
    }

    @FunctionalInterface
    private interface SqlWork<T> {

        T run(Connection connection) throws Exception;
    }
}
