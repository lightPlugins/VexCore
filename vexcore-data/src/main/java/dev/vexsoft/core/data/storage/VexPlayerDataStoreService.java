package dev.vexsoft.core.data.storage;

import dev.vexsoft.core.api.configuration.ConfigurationService;
import dev.vexsoft.core.api.configuration.VexConfiguration;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.Locale;
import java.util.Objects;
import java.nio.file.Path;
import lombok.Getter;

@Dependencies(ConfigurationService.class)
public final class VexPlayerDataStoreService implements PlayerDataStoreService, AutoCloseable {

  @Getter
  private final PlayerDataStore store;

  public VexPlayerDataStoreService(final VexServiceRegistry services) {
    VexConfiguration configuration = Objects.requireNonNull(services, "services")
        .require(ConfigurationService.class)
        .load(Path.of("database.yml"), "database.yml");
    String type = configuration.getString("storage", "postgresql").toLowerCase(Locale.ROOT);
    store = switch (type) {
      case "memory" -> new MemoryPlayerDataStore();
      case "postgresql" -> new PostgresPlayerDataStore(
          configuration.getString("postgresql.jdbc-url", "jdbc:postgresql://localhost:5432/vexcore"),
          configuration.getString("postgresql.username", "postgres"),
          configuration.getString("postgresql.password", "change-me"),
          configuration.getInt("postgresql.maximum-pool-size", 10)
      );
      default -> throw new IllegalArgumentException("Unsupported player data storage: " + type);
    };
  }

  @Override
  public void close() {
    store.close();
  }
}
