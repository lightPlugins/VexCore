package dev.vexsoft.core.data.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.vexsoft.core.api.player.DataContainerKey;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PostgresPlayerDataStore implements PlayerDataStore {

  private final HikariDataSource dataSource;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public PostgresPlayerDataStore(
      final String jdbcUrl,
      final String username,
      final String password,
      final int maximumPoolSize
  ) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(Objects.requireNonNull(jdbcUrl, "jdbcUrl"));
    config.setUsername(Objects.requireNonNull(username, "username"));
    config.setPassword(Objects.requireNonNull(password, "password"));
    config.setMaximumPoolSize(maximumPoolSize);
    config.setPoolName("VexCore-Data");
    // Let the first real operation report an unavailable database after the config was generated
    config.setInitializationFailTimeout(-1);
    dataSource = new HikariDataSource(config);
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
  public void close() {
    executor.close();
    dataSource.close();
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
}
