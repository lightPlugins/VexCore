package dev.vexsoft.core.configuration;

import dev.vexsoft.core.api.configuration.ConfigurationOwner;
import dev.vexsoft.core.api.configuration.VexConfiguration;
import dev.vexsoft.core.api.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.service.DefaultServiceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class DefaultConfigurationServiceTest {
  @TempDir Path directory;

  @Test
  void copiesAndLoadsDefaults() {
    TestOwner owner = new TestOwner(directory, Map.of("config.yml", "name: Vex\ncount: 4\n"));
    VexConfiguration config = service(owner).load("config.yml");
    assertEquals("Vex", config.getString("name"));
    assertEquals(4, config.getInt("count", 0));
    assertTrue(Files.exists(directory.resolve("config.yml")));
  }

  @Test
  void mergesMissingDefaultsWithoutReplacingUserValues() throws Exception {
    Files.writeString(directory.resolve("config.yml"), "database:\n  host: production\n", StandardCharsets.UTF_8);
    TestOwner owner = new TestOwner(directory, Map.of(
        "config.yml", "database:\n  host: localhost\n  port: 3306\n"
    ));
    VexConfiguration config = service(owner).load("config.yml");
    assertEquals("production", config.getString("database.host"));
    assertEquals(3306, config.getInt("database.port", 0));
  }

  @Test
  void rejectsPathsOutsidePluginDirectory() {
    assertThrows(IllegalArgumentException.class,
        () -> service(new TestOwner(directory, Map.of())).load(Path.of("..", "outside.yml"), false));
  }

  @Test
  void loadsYamlDirectoryAndSkipsPrivateFiles() throws Exception {
    Files.createDirectories(directory.resolve("skills"));
    Files.writeString(directory.resolve("skills/mining.yml"), "enabled: true\n");
    Files.writeString(directory.resolve("skills/_template.yml"), "enabled: false\n");
    Map<Path, VexConfiguration> loaded = service(new TestOwner(directory, Map.of()))
        .loadDirectory(Path.of("skills"));
    assertEquals(1, loaded.size());
    assertTrue(loaded.containsKey(Path.of("mining.yml")));
  }

  @Test
  void supportsConcurrentAccessFromMultipleRegions() throws Exception {
    VexConfiguration configuration = service(new TestOwner(directory, Map.of()))
        .load(Path.of("shared.yml"), false);
    var executor = Executors.newFixedThreadPool(8);
    List<Future<?>> writes = new ArrayList<>();

    try {
      for (int thread = 0; thread < 8; thread++) {
        int threadId = thread;
        writes.add(executor.submit(() -> {
          for (int iteration = 0; iteration < 250; iteration++) {
            configuration.set("regions." + threadId, iteration);
            configuration.getInt("regions." + threadId, -1);
          }
        }));
      }
      for (Future<?> write : writes) {
        write.get();
      }
    } finally {
      executor.shutdown();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }

    assertEquals(249, configuration.getInt("regions.7", -1));
  }

  private ConfigurationService service(TestOwner owner) {
    VexServiceRegistry services = new DefaultServiceRegistry().scoped(owner);
    services.register(ConfigurationService.class, VexConfigurationService.class);
    services.registerQueuedServices();
    return services.require(ConfigurationService.class);
  }

  private static final class TestOwner implements ConfigurationOwner {
    private final Path directory;
    private final Map<String, String> resources;
    private final List<String> warnings = new ArrayList<>();

    private TestOwner(Path directory, Map<String, String> resources) {
      this.directory = directory;
      this.resources = resources;
    }

    @Override public Path configurationDirectory() { return directory; }
    @Override public String serviceOwnerName() { return "test"; }
    @Override public Optional<InputStream> configurationResource(String path) {
      String value = resources.get(path);
      return value == null ? Optional.empty() : Optional.of(
          new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
    }
    @Override public void configurationWarning(String message, Throwable cause) { warnings.add(message); }
  }
}
