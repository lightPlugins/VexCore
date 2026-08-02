package dev.vexsoft.core.configuration;

import dev.vexsoft.core.api.configuration.VexConfiguration;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class YamlVexConfiguration extends ConfigurateConfigurationSection implements VexConfiguration {
  private final Path file;
  private final YamlConfigurationLoader loader;

  YamlVexConfiguration(Path file) {
    super(load(file));
    this.file = file.toAbsolutePath().normalize();
    this.loader = loader(this.file);
  }

  @Override public Path file() { return file; }

  @Override
  public void reload() {
    try {
      node.from(loader.load());
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to reload configuration " + file, exception);
    }
  }

  @Override
  public void save() {
    try {
      Files.createDirectories(file.getParent());
      loader.save(node);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to save configuration " + file, exception);
    }
  }

  public CommentedConfigurationNode rootNode() { return node; }

  private static CommentedConfigurationNode load(Path file) {
    Path normalized = file.toAbsolutePath().normalize();
    try {
      Files.createDirectories(normalized.getParent());
      if (Files.notExists(normalized)) {
        Files.createFile(normalized);
      }
      return loader(normalized).load();
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to load configuration " + normalized, exception);
    }
  }

  private static YamlConfigurationLoader loader(Path file) {
    return YamlConfigurationLoader.builder().path(file).nodeStyle(NodeStyle.BLOCK).build();
  }
}
