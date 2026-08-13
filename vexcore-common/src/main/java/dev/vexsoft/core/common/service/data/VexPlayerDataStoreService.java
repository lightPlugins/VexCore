package dev.vexsoft.core.common.service.data;

import dev.vexsoft.core.common.data.MemoryPlayerDataStore;
import dev.vexsoft.core.common.data.PlayerDataStore;
import dev.vexsoft.core.common.data.PostgresPlayerDataStore;
import dev.vexsoft.core.common.data.SqlitePlayerDataStore;
import dev.vexsoft.core.common.data.global.GlobalDataStore;
import dev.vexsoft.core.common.data.identity.PlayerIdentityStore;

import dev.vexsoft.core.api.service.configuration.ConfigurationService;
import dev.vexsoft.core.api.configuration.VexConfiguration;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Locale;
import java.util.Objects;
import java.nio.file.Path;
import java.time.Duration;
import lombok.Getter;

@Dependencies(ConfigurationService.class)
public final class VexPlayerDataStoreService implements PlayerDataStoreService, AutoCloseable {

  @Getter
  private final PlayerDataStore store;
  @Getter
  private final String storageType;
  @Getter
  private final Duration loginTimeout;
  @Getter
  private final String loginKickMessage;

  public VexPlayerDataStoreService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    ConfigurationService configurations = checkedServices.require(ConfigurationService.class);
    VexConfiguration configuration = configurations.load(Path.of("database.yml"), "database.yml");
    long loginTimeoutSeconds = configuration.getLong("player-data.login-timeout-seconds", 15L);
    if (loginTimeoutSeconds < 1L || loginTimeoutSeconds > 120L) {
      throw new IllegalArgumentException(
          "player-data.login-timeout-seconds must be between 1 and 120"
      );
    }
    loginTimeout = Duration.ofSeconds(loginTimeoutSeconds);
    loginKickMessage = configuration.getString(
        "player-data.login-kick-message",
        "Your player data could not be loaded. Please try again."
    ).trim();
    if (loginKickMessage.isEmpty()) {
      throw new IllegalArgumentException("player-data.login-kick-message must not be empty");
    }
    storageType = configuration.getString("storage", "sqlite").toLowerCase(Locale.ROOT);
    if (storageType.equals("sqlite") && !(checkedServices.getOwner() instanceof LocalStorageOwner)) {
      throw new IllegalStateException(
          "SQLite is only supported by a standalone Paper server; proxy setups require PostgreSQL"
      );
    }
    store = switch (storageType) {
      case "memory" -> new MemoryPlayerDataStore();
      case "sqlite" -> new SqlitePlayerDataStore(sqliteFile(
          configurations.getOwner().getConfigurationDirectory(),
          configuration.getString("sqlite.file", "vexcore.db")
      ));
      case "postgresql" -> new PostgresPlayerDataStore(
          configuration.getString("postgresql.jdbc-url", "jdbc:postgresql://localhost:5432/vexcore"),
          configuration.getString("postgresql.username", "postgres"),
          configuration.getString("postgresql.password", "change-me"),
          configuration.getInt("postgresql.maximum-pool-size", 10),
          configuration.getBoolean("postgresql.auto-create-database", true),
          configuration.getString("postgresql.maintenance-database", "postgres")
      );
      default -> throw new IllegalArgumentException(
          "Unsupported player data storage: " + storageType
      );
    };
    ((GlobalDataStore) store).reconcileGlobalData().join();
    ((PlayerIdentityStore) store).reconcilePlayerIdentities().join();
  }

  @Override
  public GlobalDataStore getGlobalStore() {
    return (GlobalDataStore) store;
  }

  @Override
  public PlayerIdentityStore getPlayerIdentityStore() {
    return (PlayerIdentityStore) store;
  }

  @Override
  public void close() {
    store.close();
  }

  private Path sqliteFile(final Path configurationDirectory, final String configuredFile) {
    Path root = Objects.requireNonNull(configurationDirectory, "configurationDirectory")
        .toAbsolutePath()
        .normalize();
    Path relative = Path.of(Objects.requireNonNull(configuredFile, "configuredFile").trim());
    if (relative.toString().isBlank() || relative.isAbsolute()) {
      throw new IllegalArgumentException("sqlite.file must be a non-empty relative path");
    }
    Path resolved = root.resolve(relative).normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("sqlite.file must stay inside the VexCore directory");
    }
    return resolved;
  }
}
