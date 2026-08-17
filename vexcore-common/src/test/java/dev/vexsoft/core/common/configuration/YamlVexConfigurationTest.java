package dev.vexsoft.core.common.configuration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlVexConfigurationTest {

  @Test
  void savesBlockCollectionsWithTwoSpaceIndentation(@TempDir final Path directory)
      throws IOException {
    Path file = directory.resolve("worlds.yml");
    YamlVexConfiguration configuration = new YamlVexConfiguration(file);
    LinkedHashMap<String, Object> world = new LinkedHashMap<>();
    world.put("key", "vexessentials:test");
    world.put("generator", "flat");
    configuration.set("worlds", List.of(world));

    configuration.save();

    String yaml = Files.readString(file);
    assertTrue(yaml.contains("worlds:\n- key: vexessentials:test\n  generator: flat\n"), yaml);
  }
}
