package dev.vexsoft.core.common.service.data;

import dev.vexsoft.core.common.data.MemoryPlayerDataStore;
import dev.vexsoft.core.common.data.PlayerDataStore;
import dev.vexsoft.core.common.data.PostgresPlayerDataStore;
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
    VexConfiguration configuration = Objects.requireNonNull(services, "services")
        .require(ConfigurationService.class)
        .load(Path.of("database.yml"), "database.yml");
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
    storageType = configuration.getString("storage", "postgresql").toLowerCase(Locale.ROOT);
    store = switch (storageType) {
      case "memory" -> new MemoryPlayerDataStore();
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
}
