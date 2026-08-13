package dev.vexsoft.core.common.data;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.identity.PlayerIdentity;
import dev.vexsoft.core.common.data.global.GlobalDataReference;
import dev.vexsoft.core.common.data.global.GlobalDataStore;
import dev.vexsoft.core.common.data.global.StoredGlobalData;
import dev.vexsoft.core.common.data.identity.PlayerIdentityStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.sqlite.SQLiteDataSource;

/** Persistent single-process player data storage backed by a local SQLite file. */
public final class SqlitePlayerDataStore implements
    PlayerDataStore,
    GlobalDataStore,
    PlayerIdentityStore {

  private static final String GLOBAL_DATA_TABLE = "\"vex_global_data\"";
  private static final String PLAYER_IDENTITIES_TABLE = "\"vex_player_identities\"";
  private static final int BUSY_TIMEOUT_MILLIS = 5_000;

  private final SQLiteDataSource dataSource = new SQLiteDataSource();
  private final ExecutorService executor = Executors.newSingleThreadExecutor(
      Thread.ofVirtual().name("VexCore-SQLite", 0).factory()
  );
  private final CopyOnWriteArrayList<Consumer<GlobalDataReference>> globalListeners =
      new CopyOnWriteArrayList<>();

  public SqlitePlayerDataStore(final Path file) {
    Path databaseFile = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    Path parent = databaseFile.getParent();
    if (parent == null) {
      throw new IllegalArgumentException("SQLite database file must have a parent directory");
    }
    try {
      Files.createDirectories(parent);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to create SQLite database directory " + parent, exception);
    }
    dataSource.setUrl("jdbc:sqlite:" + databaseFile);
    initializeDatabase(databaseFile);
  }

  @Override
  public CompletableFuture<Void> reconcile(
      final String owner,
      final Collection<DataContainerKey<?>> keys
  ) {
    String table = table(owner);
    return CompletableFuture.runAsync(() -> {
      try (Connection connection = connection(); Statement statement = connection.createStatement()) {
        statement.executeUpdate(
            "CREATE TABLE IF NOT EXISTS " + table
                + " (player_id TEXT PRIMARY KEY, player_name TEXT NOT NULL)"
        );
        for (DataContainerKey<?> key : keys) {
          String name = key.getName();
          if (!columns(connection, table).containsKey(name)) {
            String container = column(name);
            statement.executeUpdate(
                "ALTER TABLE " + table + " ADD COLUMN " + container
                    + " TEXT CHECK (" + container + " IS NULL OR json_valid(" + container + "))"
            );
          }
        }
      } catch (SQLException exception) {
        throw failure("Unable to reconcile player data table " + table, exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<Map<String, String>> load(
      final String owner,
      final UUID uniqueId,
      final Collection<DataContainerKey<?>> keys
  ) {
    if (keys.isEmpty()) {
      return CompletableFuture.completedFuture(Map.of());
    }
    String selected = keys.stream().map(key -> column(key.getName()))
        .reduce((left, right) -> left + ", " + right).orElseThrow();
    String sql = "SELECT " + selected + " FROM " + table(owner) + " WHERE player_id = ?";
    return CompletableFuture.supplyAsync(() -> {
      Map<String, String> values = new LinkedHashMap<>();
      try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, uniqueId.toString());
        try (ResultSet result = statement.executeQuery()) {
          if (result.next()) {
            for (DataContainerKey<?> key : keys) {
              String value = result.getString(key.getName());
              if (value != null) {
                values.put(key.getName(), value);
              }
            }
          }
        }
      } catch (SQLException exception) {
        throw failure("Unable to load player data for " + uniqueId, exception);
      }
      return Map.copyOf(values);
    }, executor);
  }

  @Override
  public CompletableFuture<Void> save(
      final String owner,
      final UUID uniqueId,
      final String playerName,
      final Map<String, String> values
  ) {
    if (values.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    String columns = values.keySet().stream().map(SqlitePlayerDataStore::column)
        .reduce((left, right) -> left + ", " + right).orElseThrow();
    String placeholders = values.keySet().stream().map(ignored -> "?")
        .reduce((left, right) -> left + ", " + right).orElseThrow();
    String updates = values.keySet().stream()
        .map(key -> column(key) + " = excluded." + column(key))
        .reduce((left, right) -> left + ", " + right).orElseThrow();
    String sql = "INSERT INTO " + table(owner) + " (player_id, player_name, " + columns
        + ") VALUES (?, ?, " + placeholders + ") ON CONFLICT(player_id) DO UPDATE SET "
        + "player_name = excluded.player_name, " + updates;
    return CompletableFuture.runAsync(() -> {
      try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, uniqueId.toString());
        statement.setString(2, playerName);
        int index = 3;
        for (String value : values.values()) {
          statement.setString(index++, value);
        }
        statement.executeUpdate();
      } catch (SQLException exception) {
        throw failure("Unable to save player data for " + uniqueId, exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<Integer> reset(
      final String owner,
      final UUID uniqueId,
      final Collection<String> containers
  ) {
    if (containers.isEmpty()) {
      return CompletableFuture.completedFuture(0);
    }
    String sql = "UPDATE " + table(owner) + " SET " + resetAssignments(containers)
        + " WHERE player_id = ?";
    return CompletableFuture.supplyAsync(() -> {
      try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, uniqueId.toString());
        return statement.executeUpdate();
      } catch (SQLException exception) {
        throw failure("Unable to reset player data for " + uniqueId, exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<Integer> resetAll(
      final String owner,
      final Collection<String> containers
  ) {
    if (containers.isEmpty()) {
      return CompletableFuture.completedFuture(0);
    }
    String sql = "UPDATE " + table(owner) + " SET " + resetAssignments(containers);
    return CompletableFuture.supplyAsync(() -> {
      try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
        return statement.executeUpdate();
      } catch (SQLException exception) {
        throw failure("Unable to reset player data table " + table(owner), exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<Optional<UUID>> findUniqueId(
      final String owner,
      final String playerName
  ) {
    String sql = "SELECT player_id FROM " + table(owner)
        + " WHERE player_name = ? COLLATE NOCASE LIMIT 1";
    return CompletableFuture.supplyAsync(() -> {
      try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, playerName);
        try (ResultSet result = statement.executeQuery()) {
          return result.next()
              ? Optional.of(UUID.fromString(result.getString("player_id")))
              : Optional.empty();
        }
      } catch (SQLException exception) {
        throw failure("Unable to resolve stored player " + playerName, exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<Void> reconcileGlobalData() {
    return executeSchema(
        "CREATE TABLE IF NOT EXISTS " + GLOBAL_DATA_TABLE
            + " (owner TEXT NOT NULL, data_key TEXT NOT NULL,"
            + " value TEXT NOT NULL CHECK (json_valid(value)),"
            + " revision INTEGER NOT NULL, updated_at INTEGER NOT NULL,"
            + " PRIMARY KEY (owner, data_key))",
        "Unable to reconcile global data storage"
    );
  }

  @Override
  public CompletableFuture<Optional<StoredGlobalData>> loadGlobalData(
      final String owner,
      final String key
  ) {
    return CompletableFuture.supplyAsync(() -> loadGlobalDataNow(owner, key), executor);
  }

  @Override
  public CompletableFuture<StoredGlobalData> setGlobalData(
      final String owner,
      final String key,
      final String value
  ) {
    String sql = "INSERT INTO " + GLOBAL_DATA_TABLE
        + " (owner, data_key, value, revision, updated_at) VALUES (?, ?, ?, 1, ?)"
        + " ON CONFLICT(owner, data_key) DO UPDATE SET value = excluded.value,"
        + " revision = revision + 1, updated_at = excluded.updated_at";
    return CompletableFuture.supplyAsync(() -> {
      try (Connection connection = connection()) {
        inTransaction(connection, () -> {
          try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner);
            statement.setString(2, key);
            statement.setString(3, value);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
          }
        });
        StoredGlobalData stored = loadGlobalDataNow(connection, owner, key).orElseThrow();
        notifyGlobalChange(new GlobalDataReference(owner, key));
        return stored;
      } catch (SQLException exception) {
        throw failure("Unable to store global data " + owner + ':' + key, exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<Optional<StoredGlobalData>> compareAndSetGlobalData(
      final String owner,
      final String key,
      final long expectedRevision,
      final String value
  ) {
    return CompletableFuture.supplyAsync(() -> {
      String sql = expectedRevision == 0
          ? "INSERT INTO " + GLOBAL_DATA_TABLE
              + " (owner, data_key, value, revision, updated_at) VALUES (?, ?, ?, 1, ?)"
              + " ON CONFLICT(owner, data_key) DO NOTHING"
          : "UPDATE " + GLOBAL_DATA_TABLE
              + " SET value = ?, revision = revision + 1, updated_at = ?"
              + " WHERE owner = ? AND data_key = ? AND revision = ?";
      try (Connection connection = connection()) {
        final int[] changed = new int[1];
        inTransaction(connection, () -> {
          try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (expectedRevision == 0) {
              statement.setString(1, owner);
              statement.setString(2, key);
              statement.setString(3, value);
              statement.setLong(4, System.currentTimeMillis());
            } else {
              statement.setString(1, value);
              statement.setLong(2, System.currentTimeMillis());
              statement.setString(3, owner);
              statement.setString(4, key);
              statement.setLong(5, expectedRevision);
            }
            changed[0] = statement.executeUpdate();
          }
        });
        if (changed[0] == 0) {
          return Optional.empty();
        }
        Optional<StoredGlobalData> stored = loadGlobalDataNow(connection, owner, key);
        notifyGlobalChange(new GlobalDataReference(owner, key));
        return stored;
      } catch (SQLException exception) {
        throw failure("Unable to update global data " + owner + ':' + key, exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<Boolean> resetGlobalData(final String owner, final String key) {
    String sql = "DELETE FROM " + GLOBAL_DATA_TABLE + " WHERE owner = ? AND data_key = ?";
    return CompletableFuture.supplyAsync(() -> {
      try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, owner);
        statement.setString(2, key);
        boolean removed = statement.executeUpdate() > 0;
        if (removed) {
          notifyGlobalChange(new GlobalDataReference(owner, key));
        }
        return removed;
      } catch (SQLException exception) {
        throw failure("Unable to reset global data " + owner + ':' + key, exception);
      }
    }, executor);
  }

  @Override
  public AutoCloseable subscribeGlobalDataChanges(
      final Consumer<GlobalDataReference> listener
  ) {
    Consumer<GlobalDataReference> checked = Objects.requireNonNull(listener, "listener");
    globalListeners.add(checked);
    return () -> globalListeners.remove(checked);
  }

  @Override
  public CompletableFuture<Void> reconcilePlayerIdentities() {
    return CompletableFuture.runAsync(() -> {
      try (Connection connection = connection(); Statement statement = connection.createStatement()) {
        statement.executeUpdate(
            "CREATE TABLE IF NOT EXISTS " + PLAYER_IDENTITIES_TABLE
                + " (player_id TEXT PRIMARY KEY, player_name TEXT NOT NULL, updated_at INTEGER NOT NULL)"
        );
        statement.executeUpdate(
            "CREATE INDEX IF NOT EXISTS \"vex_player_identities_name_idx\" ON "
                + PLAYER_IDENTITIES_TABLE + " (player_name COLLATE NOCASE)"
        );
      } catch (SQLException exception) {
        throw failure("Unable to reconcile player identity storage", exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<PlayerIdentity> recordPlayerIdentity(
      final UUID uniqueId,
      final String name
  ) {
    String sql = "INSERT INTO " + PLAYER_IDENTITIES_TABLE
        + " (player_id, player_name, updated_at) VALUES (?, ?, ?)"
        + " ON CONFLICT(player_id) DO UPDATE SET player_name = excluded.player_name,"
        + " updated_at = excluded.updated_at";
    return CompletableFuture.supplyAsync(() -> {
      long updatedAt = System.currentTimeMillis();
      try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, uniqueId.toString());
        statement.setString(2, name);
        statement.setLong(3, updatedAt);
        statement.executeUpdate();
        return new PlayerIdentity(uniqueId, name, Instant.ofEpochMilli(updatedAt));
      } catch (SQLException exception) {
        throw failure("Unable to record player identity " + uniqueId, exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<Optional<PlayerIdentity>> findPlayerIdentity(final UUID uniqueId) {
    return findPlayerIdentity(
        "SELECT player_id, player_name, updated_at FROM " + PLAYER_IDENTITIES_TABLE
            + " WHERE player_id = ?",
        uniqueId.toString()
    );
  }

  @Override
  public CompletableFuture<Optional<PlayerIdentity>> findPlayerIdentity(final String name) {
    return findPlayerIdentity(
        "SELECT player_id, player_name, updated_at FROM " + PLAYER_IDENTITIES_TABLE
            + " WHERE player_name = ? COLLATE NOCASE ORDER BY updated_at DESC LIMIT 1",
        name
    );
  }

  @Override
  public void close() {
    globalListeners.clear();
    executor.close();
  }

  private void initializeDatabase(final Path file) {
    try (Connection connection = connection(); Statement statement = connection.createStatement()) {
      String journalMode;
      try (ResultSet result = statement.executeQuery("PRAGMA journal_mode = WAL")) {
        journalMode = result.next() ? result.getString(1) : "";
      }
      if (!"wal".equalsIgnoreCase(journalMode)) {
        throw new IllegalStateException("SQLite could not enable WAL mode for " + file);
      }
      statement.execute("PRAGMA synchronous = NORMAL");
    } catch (SQLException exception) {
      throw failure("Unable to initialize SQLite database " + file, exception);
    }
  }

  private Connection connection() throws SQLException {
    Connection connection = dataSource.getConnection();
    try (Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MILLIS);
      statement.execute("PRAGMA foreign_keys = ON");
    } catch (SQLException exception) {
      connection.close();
      throw exception;
    }
    return connection;
  }

  private CompletableFuture<Void> executeSchema(final String sql, final String failureMessage) {
    return CompletableFuture.runAsync(() -> {
      try (Connection connection = connection(); Statement statement = connection.createStatement()) {
        statement.executeUpdate(sql);
      } catch (SQLException exception) {
        throw failure(failureMessage, exception);
      }
    }, executor);
  }

  private Optional<StoredGlobalData> loadGlobalDataNow(final String owner, final String key) {
    try (Connection connection = connection()) {
      return loadGlobalDataNow(connection, owner, key);
    } catch (SQLException exception) {
      throw failure("Unable to load global data " + owner + ':' + key, exception);
    }
  }

  private Optional<StoredGlobalData> loadGlobalDataNow(
      final Connection connection,
      final String owner,
      final String key
  ) throws SQLException {
    String sql = "SELECT value, revision FROM " + GLOBAL_DATA_TABLE
        + " WHERE owner = ? AND data_key = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, owner);
      statement.setString(2, key);
      try (ResultSet result = statement.executeQuery()) {
        return result.next()
            ? Optional.of(new StoredGlobalData(result.getString("value"), result.getLong("revision")))
            : Optional.empty();
      }
    }
  }

  private CompletableFuture<Optional<PlayerIdentity>> findPlayerIdentity(
      final String sql,
      final String parameter
  ) {
    return CompletableFuture.supplyAsync(() -> {
      try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, parameter);
        try (ResultSet result = statement.executeQuery()) {
          if (!result.next()) {
            return Optional.empty();
          }
          return Optional.of(new PlayerIdentity(
              UUID.fromString(result.getString("player_id")),
              result.getString("player_name"),
              Instant.ofEpochMilli(result.getLong("updated_at"))
          ));
        }
      } catch (SQLException exception) {
        throw failure("Unable to find a stored player identity", exception);
      }
    }, executor);
  }

  private Map<String, Boolean> columns(final Connection connection, final String table)
      throws SQLException {
    Map<String, Boolean> columns = new LinkedHashMap<>();
    try (Statement statement = connection.createStatement();
         ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ')')) {
      while (result.next()) {
        columns.put(result.getString("name"), true);
      }
    }
    return columns;
  }

  private void inTransaction(final Connection connection, final SqlAction action)
      throws SQLException {
    connection.setAutoCommit(false);
    try {
      action.run();
      connection.commit();
    } catch (SQLException | RuntimeException exception) {
      connection.rollback();
      throw exception;
    } finally {
      connection.setAutoCommit(true);
    }
  }

  private void notifyGlobalChange(final GlobalDataReference reference) {
    globalListeners.forEach(listener -> listener.accept(reference));
  }

  private static IllegalStateException failure(final String message, final SQLException cause) {
    return new IllegalStateException(message, cause);
  }

  private static String table(final String owner) {
    return column("vex_" + owner + "_players");
  }

  private static String column(final String value) {
    if (!value.matches("[a-z][a-z0-9_]{0,62}")) {
      throw new IllegalArgumentException("Unsafe SQL identifier: " + value);
    }
    return '"' + value + '"';
  }

  private static String resetAssignments(final Collection<String> containers) {
    return containers.stream().map(container -> column(container) + " = NULL")
        .reduce((left, right) -> left + ", " + right).orElseThrow();
  }

  @FunctionalInterface
  private interface SqlAction {
    void run() throws SQLException;
  }
}
