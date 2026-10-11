package dev.vexsoft.core.common.data.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/** Routes live record keys to plugin schemas and container tables without changing Redis identities. */
final class JdbcCheckpointRecords {

    private static final long SCHEMA_LOCK = 0x564558434f524532L;
    private final ObjectMapper mapper = new ObjectMapper();

    static boolean postgres(final Connection connection) throws SQLException {
        return connection.getMetaData().getDatabaseProductName().equals("PostgreSQL");
    }

    static String table(final Connection connection, final String owner, final String name) throws SQLException {
        identifier(owner);
        identifier(name);
        return postgres(connection) ? '"' + owner + "\".\"" + name + '"' : '"' + owner + "__" + name + '"';
    }

    static void lockSchema(final Connection connection) throws SQLException {
        if (postgres(connection)) {
            try (var lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                lock.setLong(1, SCHEMA_LOCK);
                lock.execute();
            }
        }
    }

    Map<String, String> read(final Connection connection, final String namespace) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (var address : tables(connection, namespace)) {
            try (var select = connection.prepareStatement("SELECT record_key, revision, value FROM "
                + table(connection, address.owner(), address.name()) + " WHERE namespace = ?")) {
                select.setString(1, namespace);
                try (var rows = select.executeQuery()) {
                    while (rows.next()) {
                        String key = rows.getString(1);
                        String value = rows.getString(3);
                        if (key.startsWith("n|")) {
                            value = mapper.readTree(value).textValue();
                        }
                        result.put(key, mapper.writeValueAsString(Map.of("revision", rows.getLong(2), "value", value)));
                    }
                }
            }
        }
        return result;
    }

    void write(
        final Connection connection,
        final String namespace,
        final long generation,
        final Map<String, String> values,
        final Set<String> removed
    ) throws Exception {
        Set<Table> registered = tables(connection, namespace);
        Map<Table, Map<String, String>> grouped = new TreeMap<>();
        for (var entry : values.entrySet()) {
            grouped.computeIfAbsent(route(entry.getKey()).table(), ignored -> new TreeMap<>())
                .put(entry.getKey(), entry.getValue());
        }
        for (var group : grouped.entrySet()) {
            if (!registered.contains(group.getKey())) {
                create(connection, namespace, group.getKey());
            }
            upsert(connection, namespace, generation, group.getKey(), group.getValue());
        }
        for (String key : new TreeSet<>(removed)) {
            var target = route(key).table();
            if (registered.contains(target) || grouped.containsKey(target)) {
                try (var delete = connection.prepareStatement("DELETE FROM "
                    + table(connection, target.owner(), target.name()) + " WHERE namespace = ? AND record_key = ?")) {
                    delete.setString(1, namespace);
                    delete.setString(2, key);
                    delete.executeUpdate();
                }
            }
        }
    }

    private Set<Table> tables(final Connection connection, final String namespace) throws SQLException {
        Set<Table> result = new TreeSet<>();
        try (var select = connection.prepareStatement("SELECT owner, table_name FROM "
            + table(connection, "vexcore", "checkpoint_tables") + " WHERE namespace = ? ORDER BY owner, table_name")) {
            select.setString(1, namespace);
            try (var rows = select.executeQuery()) {
                while (rows.next()) {
                    result.add(new Table(rows.getString(1), rows.getString(2)));
                }
            }
        }
        return result;
    }

    private void create(final Connection connection, final String namespace, final Table target) throws SQLException {
        // Different namespaces may introduce the same schema concurrently. Serialize only first-time DDL.
        lockSchema(connection);
        boolean postgres = postgres(connection);
        String name = table(connection, target.owner(), target.name());
        try (var statement = connection.createStatement()) {
            if (postgres) {
                statement.executeUpdate("CREATE SCHEMA IF NOT EXISTS \"" + target.owner() + "\"");
            }
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + name
                + " (namespace VARCHAR(256) NOT NULL, record_key TEXT NOT NULL, scope VARCHAR(16) NOT NULL,"
                + " domain TEXT, player_uuid " + (postgres ? "UUID" : "TEXT")
                + ", profile_uuid " + (postgres ? "UUID" : "TEXT")
                + ", container_key VARCHAR(63) NOT NULL, revision BIGINT NOT NULL, value "
                + (postgres ? "JSONB" : "TEXT") + " NOT NULL, updated_checkpoint BIGINT NOT NULL,"
                + " PRIMARY KEY (namespace, record_key))");
            String index = "checkpoint_" + UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS \"" + index + "_player\" ON "
                + name + " (namespace, player_uuid)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS \"" + index + "_profile\" ON "
                + name + " (namespace, profile_uuid)");
        }
        try (var insert = connection.prepareStatement("INSERT INTO "
            + table(connection, "vexcore", "checkpoint_tables")
            + " (namespace, owner, table_name) VALUES (?, ?, ?) ON CONFLICT DO NOTHING")) {
            insert.setString(1, namespace);
            insert.setString(2, target.owner());
            insert.setString(3, target.name());
            insert.executeUpdate();
        }
    }

    private void upsert(
        final Connection connection,
        final String namespace,
        final long generation,
        final Table target,
        final Map<String, String> values
    ) throws Exception {
        boolean postgres = postgres(connection);
        String sql = "INSERT INTO " + table(connection, target.owner(), target.name())
            + " (namespace, record_key, scope, domain, player_uuid, profile_uuid, container_key, revision, value,"
            + " updated_checkpoint) VALUES (?, ?, ?, ?, ?, ?, ?, ?, " + (postgres ? "CAST(? AS JSONB)" : "?")
            + ", ?) ON CONFLICT(namespace, record_key) DO UPDATE SET revision = excluded.revision,"
            + " value = excluded.value, updated_checkpoint = excluded.updated_checkpoint";
        try (var insert = connection.prepareStatement(sql)) {
            for (var entry : values.entrySet()) {
                var address = route(entry.getKey());
                var record = mapper.readTree(entry.getValue());
                if (!record.has("revision") || !record.path("value").isTextual()) {
                    throw new IllegalArgumentException("Invalid checkpoint record: " + entry.getKey());
                }
                String value = record.path("value").textValue();
                if (address.scope().equals("name")) {
                    value = mapper.writeValueAsString(value);
                } else if (mapper.readTree(value) == null) {
                    throw new IllegalArgumentException("Missing checkpoint JSON value: " + entry.getKey());
                }
                insert.setString(1, namespace);
                insert.setString(2, entry.getKey());
                insert.setString(3, address.scope());
                insert.setString(4, address.domain());
                uuid(insert, 5, address.player(), postgres);
                uuid(insert, 6, address.profile(), postgres);
                insert.setString(7, address.container());
                insert.setLong(8, record.path("revision").longValue());
                insert.setString(9, value);
                insert.setLong(10, generation);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void uuid(
        final PreparedStatement statement, final int index, final String value, final boolean postgres
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, postgres ? Types.OTHER : Types.VARCHAR);
        } else if (postgres) {
            statement.setObject(index, UUID.fromString(value));
        } else {
            statement.setString(index, UUID.fromString(value).toString());
        }
    }

    private static Address route(final String key) {
        String[] parts = key.split("\\|", -1);
        if (parts.length == 2 && parts[0].equals("i")) {
            return new Address(new Table("vexcore", "players"), "identity", null, parts[1], null, "identity");
        }
        if (parts.length == 2 && parts[0].equals("n")) {
            return new Address(new Table("vexcore", "player_names"), "name", null, null, null, parts[1]);
        }
        if (parts.length == 3 && parts[0].equals("g")) {
            return new Address(target(parts[1], parts[2]), "global", null, null, null, parts[2]);
        }
        if (parts.length == 4 && parts[0].equals("a")) {
            return new Address(target(parts[1], parts[3]), "account", null, parts[2], null, parts[3]);
        }
        if (parts.length == 4 && parts[0].equals("p")) {
            if (parts[1].startsWith("account/")) {
                return new Address(target(parts[2], parts[3]), "account", null,
                    parts[1].substring(8), null, parts[3]);
            }
            if (parts[1].startsWith("profile/")) {
                int memberStart = parts[1].lastIndexOf('/');
                int profileStart = parts[1].lastIndexOf('/', memberStart - 1);
                String member = parts[1].substring(memberStart + 1);
                return new Address(target(parts[2], parts[3]), member.equals("shared") ? "shared" : "personal",
                    parts[1].substring(8, profileStart), member.equals("shared") ? null : member,
                    parts[1].substring(profileStart + 1, memberStart), parts[3]);
            }
        }
        throw new IllegalArgumentException("Unsupported checkpoint record key: " + key);
    }

    private static Table target(final String owner, final String container) {
        // Legacy profile and global owners retain plugin display names; only SQL schemas use lowercase.
        String schema = owner.toLowerCase(Locale.ROOT);
        identifier(schema);
        identifier(container);
        String base = container.replaceFirst("_[a-f0-9]{32}$", "");
        String name = base;
        if (schema.equals("vexcore")) {
            name = switch (base) {
                case "native_state" -> "inventories";
                case "native_inbox" -> "delivery_inboxes";
                case "session_lease", "session_heartbeat", "session_retirement" -> "profile_sessions";
                default -> base.startsWith("published_") ? "published_configurations" : base;
            };
        } else if (schema.equals("vexisles")) {
            name = switch (base) {
                case "profiles" -> "profile_accounts";
                case "roster" -> "profiles";
                case "membership" -> "profile_members";
                case "departure" -> "profile_departures";
                default -> base;
            };
        }
        // Keep infrastructure and alias names disjoint from freely registered container names.
        if (!name.equals(base) || Set.of("checkpoints", "checkpoint_tables", "players", "player_names",
            "inventories", "delivery_inboxes", "profile_sessions", "published_configurations", "profile_accounts",
            "profile_members", "profile_departures").contains(base)) {
            if (name.equals(base)) {
                name = "container_" + base;
            }
        }
        if (name.length() > 63) {
            name = name.substring(0, 30) + '_' + UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        }
        return new Table(schema, name);
    }

    private static void identifier(final String name) {
        if (!name.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("Invalid checkpoint SQL identifier: " + name);
        }
    }

    private record Address(Table table, String scope, String domain, String player, String profile, String container) {
    }

    private record Table(String owner, String name) implements Comparable<Table> {

        @Override
        public int compareTo(final Table other) {
            int ownerOrder = owner.compareTo(other.owner);
            return ownerOrder == 0 ? name.compareTo(other.name) : ownerOrder;
        }
    }
}
