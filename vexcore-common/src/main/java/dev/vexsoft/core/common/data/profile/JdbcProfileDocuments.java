package dev.vexsoft.core.common.data.profile;

import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.player.profile.storage.StoredProfileDocument;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Executes profile transactions on an existing pool/executor without owning another connection layer. */
public final class JdbcProfileDocuments implements ProfileDocumentStore {

    private static final String TABLE = "vex_profile_documents";
    private static final String SELECT = "SELECT value, revision FROM " + TABLE
        + " WHERE address = ? AND owner = ? AND name = ?";
    private static final String INSERT = "INSERT INTO " + TABLE
        + " (address, owner, name, value, revision) VALUES (?, ?, ?, '{}', 0)"
        + " ON CONFLICT(address, owner, name) DO NOTHING";
    private static final String UPDATE = "UPDATE " + TABLE
        + " SET value = ?, revision = revision + 1 WHERE address = ? AND owner = ? AND name = ? AND revision = ?";

    private final Connections connections;
    private final Executor executor;
    private final boolean postgres;
    private final Semaphore capacity = new Semaphore(512);

    public JdbcProfileDocuments(final Connections connections, final Executor executor, final boolean postgres) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.postgres = postgres;
    }

    @Override
    public CompletableFuture<Void> reconcileProfileDocuments() {
        return submit(() -> {
            try (Connection connection = connections.open(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE
                    + " (address VARCHAR(256) NOT NULL, owner VARCHAR(51) NOT NULL, name VARCHAR(63) NOT NULL,"
                    + " value TEXT NOT NULL, revision BIGINT NOT NULL, PRIMARY KEY(address, owner, name))");
                return null;
            } catch (SQLException failure) {
                throw databaseFailure(failure);
            }
        });
    }

    @Override
    public CompletableFuture<Map<ProfileDocumentKey, StoredProfileDocument>> loadProfileDocuments(
        final Collection<ProfileDocumentKey> keys
    ) {
        List<ProfileDocumentKey> captured = List.copyOf(keys);
        requireBounded(captured.size());
        if (captured.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return submit(() -> {
            Map<ProfileDocumentKey, StoredProfileDocument> result = new LinkedHashMap<>();
            Map<StoredKey, ProfileDocumentKey> requested = new LinkedHashMap<>();
            captured.forEach(key -> requested.put(new StoredKey(key.address().storageKey(), key.owner(), key.name()), key));
            // Only constant placeholders enter SQL; all actual identifiers are bound. One round trip reads the batch.
            String query = "SELECT address, owner, name, value, revision FROM " + TABLE
                + " WHERE (address, owner, name) IN ("
                + String.join(",", Collections.nCopies(captured.size(), "(?,?,?)")) + ")";
            try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(query)) {
                int index = 1;
                for (ProfileDocumentKey key : captured) {
                    bindKey(statement, key, index);
                    index += 3;
                }
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        if (rows.getLong("revision") > 0) {
                            var stored = new StoredKey(rows.getString("address"), rows.getString("owner"),
                                rows.getString("name"));
                            result.put(Objects.requireNonNull(requested.get(stored)),
                                new StoredProfileDocument(rows.getString("value"), rows.getLong("revision")));
                        }
                    }
                }
                return Map.copyOf(result);
            } catch (SQLException failure) {
                throw databaseFailure(failure);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> commitProfileDocuments(final Collection<ProfileDocumentChange> changes) {
        List<ProfileDocumentChange> captured = changes.stream()
            .sorted((left, right) -> left.key().compareTo(right.key())).toList();
        requireBounded(captured.size());
        if (captured.stream().map(ProfileDocumentChange::key).distinct().count() != captured.size()) {
            throw new IllegalArgumentException("Duplicate profile document in transaction");
        }
        return submit(() -> commit(captured));
    }

    @Override
    public CompletableFuture<Long> profileDatabaseTime() {
        return submit(() -> {
            String query = postgres ? "SELECT CAST(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000 AS BIGINT)"
                : "SELECT CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)";
            try (Connection connection = connections.open();
                 Statement statement = connection.createStatement();
                 var rows = statement.executeQuery(query)) {
                if (!rows.next()) {
                    throw new IllegalStateException("Storage did not supply its time");
                }
                return rows.getLong(1);
            } catch (SQLException failure) {
                throw databaseFailure(failure);
            }
        });
    }

    private boolean commit(final List<ProfileDocumentChange> changes) {
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(INSERT);
                 PreparedStatement select = connection.prepareStatement(SELECT + (postgres ? " FOR UPDATE" : ""));
                 PreparedStatement update = connection.prepareStatement(UPDATE)) {
                // Even absent rows acquire a transaction-owned placeholder before revision validation.
                // Stable ordering prevents competing cross-profile transactions from taking opposite locks.
                for (ProfileDocumentChange change : changes) {
                    bindKey(insert, change.key(), 1);
                    insert.executeUpdate();
                    bindKey(select, change.key(), 1);
                    try (var rows = select.executeQuery()) {
                        if (!rows.next() || rows.getLong("revision") != change.expectedRevision()) {
                            connection.rollback();
                            return false;
                        }
                    }
                    if (change.value() != null) {
                        update.setString(1, change.value());
                        bindKey(update, change.key(), 2);
                        update.setLong(5, change.expectedRevision());
                        if (update.executeUpdate() != 1) {
                            connection.rollback();
                            return false;
                        }
                    }
                }
                long deadline = changes.stream().mapToLong(ProfileDocumentChange::notAfterEpochMillis)
                    .filter(value -> value > 0).min().orElse(0);
                if (deadline > 0) {
                    String clock = postgres ? "SELECT CAST(EXTRACT(EPOCH FROM clock_timestamp()) * 1000 AS BIGINT)"
                        : "SELECT CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)";
                    try (Statement statement = connection.createStatement(); var time = statement.executeQuery(clock)) {
                        if (!time.next() || time.getLong(1) >= deadline) {
                            connection.rollback();
                            return false;
                        }
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        } catch (SQLException failure) {
            throw databaseFailure(failure);
        }
    }

    private <T> CompletableFuture<T> submit(final Supplier<T> operation) {
        if (!capacity.tryAcquire()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Profile storage queue is full"));
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return operation.get();
                } finally {
                    capacity.release();
                }
            }, executor);
        } catch (RuntimeException failure) {
            capacity.release();
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void bindKey(final PreparedStatement statement, final ProfileDocumentKey key, final int start)
        throws SQLException {
        statement.setString(start, key.address().storageKey());
        statement.setString(start + 1, key.owner());
        statement.setString(start + 2, key.name());
    }

    private static void requireBounded(final int size) {
        if (size > 1024) {
            throw new IllegalArgumentException("Profile transaction exceeds 1024 documents");
        }
    }

    private static IllegalStateException databaseFailure(final SQLException failure) {
        return new IllegalStateException("Profile storage operation failed", failure);
    }

    private record StoredKey(String address, String owner, String name) {
    }

    /** Opens a connection from the backend's existing configured connection source. */
    @FunctionalInterface
    public interface Connections {

        /** Returns a connection owned by the calling transaction. */
        Connection open() throws SQLException;
    }
}
