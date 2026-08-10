package dev.vexsoft.core.common.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.common.data.global.GlobalDataReference;
import dev.vexsoft.core.common.data.global.GlobalDataStore;
import dev.vexsoft.core.common.data.global.StoredGlobalData;
import dev.vexsoft.core.api.player.identity.PlayerIdentity;
import dev.vexsoft.core.common.data.identity.PlayerIdentityStore;
import java.time.Instant;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.postgresql.ds.PGSimpleDataSource;

public final class PostgresPlayerDataStore implements
    PlayerDataStore,
    GlobalDataStore,
    PlayerIdentityStore {

  private static final String GLOBAL_DATA_TABLE = "\"vex_global_data\"";
  private static final String GLOBAL_DATA_CHANNEL = "vex_global_data";
  private static final String PLAYER_IDENTITIES_TABLE = "\"vex_player_identities\"";

  private final HikariDataSource dataSource;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final CopyOnWriteArrayList<Consumer<GlobalDataReference>> globalListeners =
      new CopyOnWriteArrayList<>();
  private final AtomicBoolean globalListenerStarted = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();
  private volatile Thread globalListenerThread;

  public PostgresPlayerDataStore(
      final String jdbcUrl,
      final String username,
      final String password,
      final int maximumPoolSize,
      final boolean autoCreateDatabase,
      final String maintenanceDatabase
  ) {
    PGSimpleDataSource postgres = new PGSimpleDataSource();
    postgres.setURL(Objects.requireNonNull(jdbcUrl, "jdbcUrl"));
    postgres.setUser(Objects.requireNonNull(username, "username"));
    postgres.setPassword(Objects.requireNonNull(password, "password"));
    if (autoCreateDatabase) {
      createDatabaseIfMissing(
          postgres,
          jdbcUrl,
          username,
          password,
          Objects.requireNonNull(maintenanceDatabase, "maintenanceDatabase")
      );
    }

    HikariConfig config = new HikariConfig();
    // A direct DataSource avoids DriverManager lookups across Paper's library classloader
    config.setDataSource(postgres);
    config.setMaximumPoolSize(maximumPoolSize);
    config.setPoolName("VexCore-Data");
    // Keep pool creation lazy when automatic database creation is disabled
    config.setInitializationFailTimeout(-1);
    dataSource = new HikariDataSource(config);
  }

  private void createDatabaseIfMissing(
      final PGSimpleDataSource target,
      final String jdbcUrl,
      final String username,
      final String password,
      final String maintenanceDatabase
  ) {
    String database = target.getDatabaseName();
    if (database == null || database.isBlank()) {
      throw new IllegalArgumentException("PostgreSQL JDBC URL does not contain a database name");
    }

    PGSimpleDataSource maintenance = new PGSimpleDataSource();
    maintenance.setURL(maintenanceUrl(jdbcUrl, maintenanceDatabase));
    maintenance.setUser(username);
    maintenance.setPassword(password);

    try (Connection connection = maintenance.getConnection()) {
      if (databaseExists(connection, database)) {
        return;
      }
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate("CREATE DATABASE " + quoteIdentifier(database));
      } catch (SQLException exception) {
        // Another server may have created the database after our existence check
        if (!"42P04".equals(exception.getSQLState())) {
          throw exception;
        }
      }
    } catch (SQLException exception) {
      throw new IllegalStateException(
          "Unable to create PostgreSQL database '" + database
              + "'. Grant CREATE DATABASE or disable postgresql.auto-create-database",
          exception
      );
    }
  }

  private boolean databaseExists(final Connection connection, final String database) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT 1 FROM pg_database WHERE datname = ?"
    )) {
      statement.setString(1, database);
      try (ResultSet result = statement.executeQuery()) {
        return result.next();
      }
    }
  }

  private String quoteIdentifier(final String identifier) {
    return '"' + identifier.replace("\"", "\"\"") + '"';
  }

  private String maintenanceUrl(final String jdbcUrl, final String maintenanceDatabase) {
    String prefix = "jdbc:postgresql:";
    if (!jdbcUrl.startsWith(prefix)) {
      throw new IllegalArgumentException("Unsupported PostgreSQL JDBC URL: " + jdbcUrl);
    }
    int query = jdbcUrl.indexOf('?');
    String parameters = query < 0 ? "" : jdbcUrl.substring(query);
    String connection = query < 0 ? jdbcUrl : jdbcUrl.substring(0, query);
    if (connection.startsWith(prefix + "//")) {
      int databaseSeparator = connection.indexOf('/', (prefix + "//").length());
      if (databaseSeparator < 0) {
        throw new IllegalArgumentException("PostgreSQL JDBC URL does not contain a database name");
      }
      return connection.substring(0, databaseSeparator + 1) + maintenanceDatabase + parameters;
    }
    return prefix + maintenanceDatabase + parameters;
  }

  @Override
  public CompletableFuture<Void> reconcile(
      final String owner,
      final Collection<DataContainerKey<?>> keys
  ) {
    String table = table(owner);
    return CompletableFuture.runAsync(() -> {
      try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
        statement.executeUpdate(
            "CREATE TABLE IF NOT EXISTS " + table
                + " (player_id UUID PRIMARY KEY, player_name VARCHAR(16) NOT NULL)"
        );
        for (DataContainerKey<?> key : keys) {
          // Nullable columns let existing rows receive the Java default on their next load
          statement.executeUpdate(
              "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + column(key.getName()) + " JSONB"
          );
        }
      } catch (SQLException exception) {
        throw new IllegalStateException("Unable to reconcile player data table " + table, exception);
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
    String selectedColumns = keys.stream()
        .map(key -> column(key.getName()))
        .reduce((left, right) -> left + ", " + right)
        .orElseThrow();
    String sql = "SELECT " + selectedColumns + " FROM " + table(owner) + " WHERE player_id = ?";
    return CompletableFuture.supplyAsync(() -> {
      Map<String, String> values = new LinkedHashMap<>();
      try (Connection connection = dataSource.getConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setObject(1, uniqueId);
        try (ResultSet result = statement.executeQuery()) {
          if (result.next()) {
            for (DataContainerKey<?> key : keys) {
              String json = result.getString(key.getName());
              if (json != null) {
                values.put(key.getName(), json);
              }
            }
          }
        }
      } catch (SQLException exception) {
        throw new IllegalStateException("Unable to load player data for " + uniqueId, exception);
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
    String columns = values.keySet().stream()
        .map(PostgresPlayerDataStore::column)
        .reduce((left, right) -> left + ", " + right)
        .orElseThrow();
    String placeholders = values.keySet().stream()
        .map(ignored -> "?::jsonb")
        .reduce((left, right) -> left + ", " + right)
        .orElseThrow();
    String updates = values.keySet().stream()
        .map(key -> column(key) + " = EXCLUDED." + column(key))
        .reduce((left, right) -> left + ", " + right)
        .orElseThrow();
    String sql = "INSERT INTO " + table(owner) + " (player_id, player_name, " + columns + ") VALUES (?, ?, "
        + placeholders + ") ON CONFLICT (player_id) DO UPDATE SET player_name = EXCLUDED.player_name, " + updates;
    return CompletableFuture.runAsync(() -> {
      try (Connection connection = dataSource.getConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setObject(1, uniqueId);
        statement.setString(2, playerName);
        int index = 3;
        for (String json : values.values()) {
          statement.setString(index++, json);
        }
        statement.executeUpdate();
      } catch (SQLException exception) {
        throw new IllegalStateException("Unable to save player data for " + uniqueId, exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<Integer> reset(
      final String owner,
      final UUID uniqueId,
      final Collection<String> containers
  ) {
    Objects.requireNonNull(uniqueId, "uniqueId");
    if (containers.isEmpty()) {
      return CompletableFuture.completedFuture(0);
    }
    String sql = "UPDATE " + table(owner) + " SET " + resetAssignments(containers)
        + " WHERE player_id = ?";
    return CompletableFuture.supplyAsync(() -> {
      try (Connection connection = dataSource.getConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setObject(1, uniqueId);
        return statement.executeUpdate();
      } catch (SQLException exception) {
        throw new IllegalStateException("Unable to reset player data for " + uniqueId, exception);
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
      try (Connection connection = dataSource.getConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        return statement.executeUpdate();
      } catch (SQLException exception) {
        throw new IllegalStateException("Unable to reset player data table " + table(owner), exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<Optional<UUID>> findUniqueId(
      final String owner,
      final String playerName
  ) {
    String sql = "SELECT player_id FROM " + table(owner)
        + " WHERE LOWER(player_name) = LOWER(?) LIMIT 1";
    return CompletableFuture.supplyAsync(() -> {
      try (Connection connection = dataSource.getConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, playerName);
        try (ResultSet result = statement.executeQuery()) {
          return result.next()
              ? Optional.of(result.getObject("player_id", UUID.class))
              : Optional.empty();
        }
      } catch (SQLException exception) {
        throw new IllegalStateException("Unable to resolve stored player " + playerName, exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<Void> reconcileGlobalData() {
    return CompletableFuture.runAsync(() -> {
      try (Connection connection = dataSource.getConnection();
           Statement statement = connection.createStatement()) {
        statement.executeUpdate(
            "CREATE TABLE IF NOT EXISTS " + GLOBAL_DATA_TABLE
                + " (owner VARCHAR(63) NOT NULL, data_key VARCHAR(63) NOT NULL,"
                + " value JSONB NOT NULL, revision BIGINT NOT NULL,"
                + " updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + " PRIMARY KEY (owner, data_key))"
        );
      } catch (SQLException exception) {
        throw new IllegalStateException("Unable to reconcile global data storage", exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<Optional<StoredGlobalData>> loadGlobalData(
      final String owner,
      final String key
  ) {
    String sql = "SELECT value, revision FROM " + GLOBAL_DATA_TABLE
        + " WHERE owner = ? AND data_key = ?";
    return CompletableFuture.supplyAsync(() -> {
      try (Connection connection = dataSource.getConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, owner);
        statement.setString(2, key);
        try (ResultSet result = statement.executeQuery()) {
          if (!result.next()) {
            return Optional.empty();
          }
          return Optional.of(new StoredGlobalData(
              result.getString("value"),
              result.getLong("revision")
          ));
        }
      } catch (SQLException exception) {
        throw new IllegalStateException(
            "Unable to load global data " + owner + ':' + key,
            exception
        );
      }
    }, executor);
  }

  @Override
  public CompletableFuture<StoredGlobalData> setGlobalData(
      final String owner,
      final String key,
      final String value
  ) {
    String sql = "INSERT INTO " + GLOBAL_DATA_TABLE
        + " (owner, data_key, value, revision) VALUES (?, ?, ?::jsonb, 1)"
        + " ON CONFLICT (owner, data_key) DO UPDATE SET value = EXCLUDED.value,"
        + " revision = " + GLOBAL_DATA_TABLE + ".revision + 1,"
        + " updated_at = CURRENT_TIMESTAMP RETURNING revision";
    return CompletableFuture.supplyAsync(() -> {
      try (Connection connection = dataSource.getConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, owner);
        statement.setString(2, key);
        statement.setString(3, value);
        try (ResultSet result = statement.executeQuery()) {
          if (!result.next()) {
            throw new IllegalStateException("PostgreSQL did not return a global data revision");
          }
          StoredGlobalData stored = new StoredGlobalData(value, result.getLong(1));
          publishGlobalChange(connection, owner, key);
          return stored;
        }
      } catch (SQLException exception) {
        throw new IllegalStateException(
            "Unable to store global data " + owner + ':' + key,
            exception
        );
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
    return expectedRevision == 0
        ? insertInitialGlobalData(owner, key, value)
        : updateExistingGlobalData(owner, key, expectedRevision, value);
  }

  @Override
  public CompletableFuture<Boolean> resetGlobalData(final String owner, final String key) {
    String sql = "DELETE FROM " + GLOBAL_DATA_TABLE + " WHERE owner = ? AND data_key = ?";
    return CompletableFuture.supplyAsync(() -> {
      try (Connection connection = dataSource.getConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, owner);
        statement.setString(2, key);
        boolean removed = statement.executeUpdate() > 0;
        if (removed) {
          publishGlobalChange(connection, owner, key);
        }
        return removed;
      } catch (SQLException exception) {
        throw new IllegalStateException(
            "Unable to reset global data " + owner + ':' + key,
            exception
        );
      }
    }, executor);
  }

  @Override
  public AutoCloseable subscribeGlobalDataChanges(
      final Consumer<GlobalDataReference> listener
  ) {
    Consumer<GlobalDataReference> checkedListener = Objects.requireNonNull(listener, "listener");
    globalListeners.add(checkedListener);
    startGlobalDataListener();
    return () -> globalListeners.remove(checkedListener);
  }

  @Override
  public CompletableFuture<Void> reconcilePlayerIdentities() {
    return CompletableFuture.runAsync(() -> {
      try (Connection connection = dataSource.getConnection();
           Statement statement = connection.createStatement()) {
        statement.executeUpdate(
            "CREATE TABLE IF NOT EXISTS " + PLAYER_IDENTITIES_TABLE
                + " (player_id UUID PRIMARY KEY, player_name VARCHAR(16) NOT NULL,"
                + " updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)"
        );
        statement.executeUpdate(
            "CREATE INDEX IF NOT EXISTS \"vex_player_identities_name_idx\" ON "
                + PLAYER_IDENTITIES_TABLE + " (LOWER(player_name))"
        );
      } catch (SQLException exception) {
        throw new IllegalStateException("Unable to reconcile player identity storage", exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<PlayerIdentity> recordPlayerIdentity(
      final UUID uniqueId,
      final String name
  ) {
    String sql = "INSERT INTO " + PLAYER_IDENTITIES_TABLE
        + " (player_id, player_name) VALUES (?, ?)"
        + " ON CONFLICT (player_id) DO UPDATE SET player_name = EXCLUDED.player_name,"
        + " updated_at = CURRENT_TIMESTAMP RETURNING updated_at";
    return CompletableFuture.supplyAsync(() -> {
      try (Connection connection = dataSource.getConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setObject(1, uniqueId);
        statement.setString(2, name);
        try (ResultSet result = statement.executeQuery()) {
          if (!result.next()) {
            throw new IllegalStateException("PostgreSQL did not return the identity timestamp");
          }
          Instant updatedAt = result.getTimestamp(1).toInstant();
          return new PlayerIdentity(uniqueId, name, updatedAt);
        }
      } catch (SQLException exception) {
        throw new IllegalStateException("Unable to record player identity " + uniqueId, exception);
      }
    }, executor);
  }

  @Override
  public CompletableFuture<Optional<PlayerIdentity>> findPlayerIdentity(
      final UUID uniqueId
  ) {
    return findPlayerIdentity(
        "SELECT player_id, player_name, updated_at FROM " + PLAYER_IDENTITIES_TABLE
            + " WHERE player_id = ?",
        statement -> statement.setObject(1, uniqueId)
    );
  }

  @Override
  public CompletableFuture<Optional<PlayerIdentity>> findPlayerIdentity(final String name) {
    return findPlayerIdentity(
        "SELECT player_id, player_name, updated_at FROM " + PLAYER_IDENTITIES_TABLE
            + " WHERE LOWER(player_name) = LOWER(?) ORDER BY updated_at DESC LIMIT 1",
        statement -> statement.setString(1, name)
    );
  }

  @Override
  public void close() {
    closed.set(true);
    Thread listener = globalListenerThread;
    if (listener != null) {
      listener.interrupt();
    }
    globalListeners.clear();
    executor.close();
    dataSource.close();
  }

  private CompletableFuture<Optional<StoredGlobalData>> insertInitialGlobalData(
      final String owner,
      final String key,
      final String value
  ) {
    String sql = "INSERT INTO " + GLOBAL_DATA_TABLE
        + " (owner, data_key, value, revision) VALUES (?, ?, ?::jsonb, 1)"
        + " ON CONFLICT (owner, data_key) DO NOTHING RETURNING revision";
    return writeComparedGlobalData(sql, owner, key, 0, value);
  }

  private CompletableFuture<Optional<StoredGlobalData>> updateExistingGlobalData(
      final String owner,
      final String key,
      final long expectedRevision,
      final String value
  ) {
    String sql = "UPDATE " + GLOBAL_DATA_TABLE
        + " SET value = ?::jsonb, revision = revision + 1, updated_at = CURRENT_TIMESTAMP"
        + " WHERE owner = ? AND data_key = ? AND revision = ? RETURNING revision";
    return writeComparedGlobalData(sql, owner, key, expectedRevision, value);
  }

  private CompletableFuture<Optional<StoredGlobalData>> writeComparedGlobalData(
      final String sql,
      final String owner,
      final String key,
      final long expectedRevision,
      final String value
  ) {
    return CompletableFuture.supplyAsync(() -> {
      try (Connection connection = dataSource.getConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        if (expectedRevision == 0) {
          statement.setString(1, owner);
          statement.setString(2, key);
          statement.setString(3, value);
        } else {
          statement.setString(1, value);
          statement.setString(2, owner);
          statement.setString(3, key);
          statement.setLong(4, expectedRevision);
        }
        try (ResultSet result = statement.executeQuery()) {
          if (!result.next()) {
            return Optional.empty();
          }
          StoredGlobalData stored = new StoredGlobalData(value, result.getLong(1));
          publishGlobalChange(connection, owner, key);
          return Optional.of(stored);
        }
      } catch (SQLException exception) {
        throw new IllegalStateException(
            "Unable to update global data " + owner + ':' + key,
            exception
        );
      }
    }, executor);
  }

  private void publishGlobalChange(
      final Connection connection,
      final String owner,
      final String key
  ) throws SQLException {
    try (PreparedStatement notification = connection.prepareStatement("SELECT pg_notify(?, ?)")) {
      notification.setString(1, GLOBAL_DATA_CHANNEL);
      notification.setString(2, owner + '\n' + key);
      notification.execute();
    }
  }

  private void startGlobalDataListener() {
    if (!globalListenerStarted.compareAndSet(false, true)) {
      return;
    }
    globalListenerThread = Thread.ofVirtual()
        .name("VexCore-GlobalData-Listener")
        .start(this::listenForGlobalDataChanges);
  }

  private void listenForGlobalDataChanges() {
    while (!closed.get()) {
      try (Connection connection = dataSource.getConnection();
           Statement statement = connection.createStatement()) {
        statement.execute("LISTEN " + GLOBAL_DATA_CHANNEL);
        PGConnection postgres = connection.unwrap(PGConnection.class);
        while (!closed.get()) {
          PGNotification[] notifications = postgres.getNotifications(1_000);
          if (notifications == null) {
            continue;
          }
          for (PGNotification notification : notifications) {
            dispatchGlobalChange(notification.getParameter());
          }
        }
      } catch (SQLException exception) {
        if (!closed.get()) {
          pauseBeforeListenerReconnect();
        }
      }
    }
  }

  private void dispatchGlobalChange(final String payload) {
    int separator = payload.indexOf('\n');
    if (separator <= 0 || separator == payload.length() - 1) {
      return;
    }
    GlobalDataReference reference = new GlobalDataReference(
        payload.substring(0, separator),
        payload.substring(separator + 1)
    );
    globalListeners.forEach(listener -> listener.accept(reference));
  }

  private void pauseBeforeListenerReconnect() {
    try {
      Thread.sleep(1_000L);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }

  private CompletableFuture<Optional<PlayerIdentity>> findPlayerIdentity(
      final String sql,
      final SqlStatementBinder binder
  ) {
    return CompletableFuture.supplyAsync(() -> {
      try (Connection connection = dataSource.getConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        binder.bind(statement);
        try (ResultSet result = statement.executeQuery()) {
          if (!result.next()) {
            return Optional.empty();
          }
          return Optional.of(new PlayerIdentity(
              result.getObject("player_id", UUID.class),
              result.getString("player_name"),
              result.getTimestamp("updated_at").toInstant()
          ));
        }
      } catch (SQLException exception) {
        throw new IllegalStateException("Unable to find a stored player identity", exception);
      }
    }, executor);
  }

  @FunctionalInterface
  private interface SqlStatementBinder {
    void bind(PreparedStatement statement) throws SQLException;
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
    return containers.stream()
        .map(container -> column(container) + " = NULL")
        .reduce((left, right) -> left + ", " + right)
        .orElseThrow();
  }
}
