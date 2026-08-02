package dev.vexsoft.core.configuration;

import dev.vexsoft.core.api.configuration.ConfigurationOwner;
import dev.vexsoft.core.api.configuration.VexConfiguration;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class DefaultPluginConfigurations {
  private final ConfigurationOwner owner;
  private final Path dataDirectory;

  DefaultPluginConfigurations(ConfigurationOwner owner) {
    this.owner = owner;
    this.dataDirectory = owner.configurationDirectory().toAbsolutePath().normalize();
  }

  public ConfigurationOwner owner() { return owner; }

  public VexConfiguration load(String relativePath) {
    return load(Path.of(Objects.requireNonNull(relativePath, "relativePath")));
  }

  public VexConfiguration load(Path relativePath) {
    return load(relativePath, true);
  }

  public VexConfiguration load(Path relativePath, boolean loadDefaults) {
    Path normalized = normalizeRelative(relativePath);
    return loadInternal(normalized, normalized.toString().replace('\\', '/'), loadDefaults);
  }

  public VexConfiguration load(Path relativePath, String defaultsResource) {
    return loadInternal(normalizeRelative(relativePath), normalizeResource(defaultsResource), true);
  }

  public Map<Path, VexConfiguration> loadDirectory(Path relativeDirectory) {
    Path directory = resolveInsideDataDirectory(relativeDirectory);
    Map<Path, VexConfiguration> loaded = new LinkedHashMap<>();
    if (Files.notExists(directory)) {
      return Map.of();
    }

    try (var files = Files.walk(directory)) {
      files.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".yml"))
          .filter(path -> !path.getFileName().toString().startsWith("_"))
          .sorted(Comparator.comparing(Path::toString))
          .forEach(path -> {
            try {
              loaded.put(directory.relativize(path), new YamlVexConfiguration(path));
            } catch (RuntimeException exception) {
              owner.configurationWarning("Failed to load YAML file " + path, exception);
            }
          });
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to scan configuration directory " + directory, exception);
    }
    return Map.copyOf(loaded);
  }

  private VexConfiguration loadInternal(Path relativePath, String resourcePath, boolean loadDefaults) {
    requireYaml(relativePath.toString());
    Path target = resolveInsideDataDirectory(relativePath);
    if (!loadDefaults) {
      return new YamlVexConfiguration(target);
    }

    requireYaml(resourcePath);
    copyDefaultIfMissing(target, resourcePath);
    YamlVexConfiguration configuration = new YamlVexConfiguration(target);
    CommentedConfigurationNode defaults = loadDefaults(resourcePath);
    boolean changed = merge(defaults, configuration.rootNode(), "", target, resourcePath);
    if (changed) {
      configuration.save();
    }
    return configuration;
  }

  private void copyDefaultIfMissing(Path target, String resourcePath) {
    if (Files.exists(target)) {
      return;
    }
    try (InputStream resource = openResource(resourcePath)) {
      Files.createDirectories(target.getParent());
      Files.copy(resource, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to copy default " + resourcePath + " to " + target, exception);
    }
  }

  private CommentedConfigurationNode loadDefaults(String resourcePath) {
    try (InputStream resource = openResource(resourcePath);
         BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
      return YamlConfigurationLoader.builder()
          .source(() -> reader)
          .nodeStyle(NodeStyle.BLOCK)
          .build()
          .load();
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to parse default resource " + resourcePath, exception);
    }
  }

  private InputStream openResource(String resourcePath) {
    return owner.configurationResource(resourcePath)
        .orElseThrow(() -> new IllegalStateException(
            "Missing configuration resource " + resourcePath + " in " + owner.serviceOwnerName()
        ));
  }

  private boolean merge(
      CommentedConfigurationNode defaults,
      CommentedConfigurationNode target,
      String path,
      Path file,
      String resourcePath
  ) {
    // Existing values always win, defaults only fill gaps and missing comments
    if (target.virtual()) {
      target.from(defaults);
      return true;
    }

    boolean changed = false;
    if ((target.comment() == null || target.comment().isBlank())
        && defaults.comment() != null && !defaults.comment().isBlank()) {
      target.comment(defaults.comment());
      changed = true;
    }

    if (defaults.isMap()) {
      if (!target.isMap()) {
        warnConflict(file, resourcePath, path, "map", describe(target));
        return changed;
      }
      for (Map.Entry<Object, ? extends ConfigurationNode> entry : defaults.childrenMap().entrySet()) {
        CommentedConfigurationNode defaultChild = asCommented(entry.getValue());
        String childPath = path.isBlank() ? String.valueOf(entry.getKey()) : path + "." + entry.getKey();
        changed |= merge(defaultChild, target.node(entry.getKey()), childPath, file, resourcePath);
      }
    } else if (defaults.isList() && !target.isList()) {
      warnConflict(file, resourcePath, path, "list", describe(target));
    } else if (!defaults.isList() && (target.isMap() || target.isList())) {
      warnConflict(file, resourcePath, path, "scalar", describe(target));
    }
    return changed;
  }

  private void warnConflict(Path file, String defaults, String path, String expected, String found) {
    owner.configurationWarning(
        "Type conflict in " + file + " at '" + (path.isBlank() ? "<root>" : path)
            + "' (defaults=" + defaults + "): expected " + expected + ", found " + found
            + ". Keeping current value.",
        null
    );
  }

  private Path normalizeRelative(Path path) {
    Objects.requireNonNull(path, "path");
    if (path.isAbsolute()) {
      throw new IllegalArgumentException("Configuration path must be relative: " + path);
    }
    return path.normalize();
  }

  private Path resolveInsideDataDirectory(Path relativePath) {
    Path resolved = dataDirectory.resolve(normalizeRelative(relativePath)).toAbsolutePath().normalize();
    // Normalizing before this check prevents paths such as ../OtherPlugin/config.yml
    if (!resolved.startsWith(dataDirectory)) {
      throw new IllegalArgumentException("Configuration path escapes plugin directory: " + relativePath);
    }
    return resolved;
  }

  private String normalizeResource(String resourcePath) {
    String normalized = Objects.requireNonNull(resourcePath, "defaultsResource").replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    if (normalized.isBlank() || normalized.contains("../")) {
      throw new IllegalArgumentException("Invalid configuration resource path: " + resourcePath);
    }
    return normalized;
  }

  private void requireYaml(String path) {
    if (!path.endsWith(".yml")) {
      throw new IllegalArgumentException("Only .yml files are supported: " + path);
    }
  }

  private CommentedConfigurationNode asCommented(ConfigurationNode node) {
    if (node instanceof CommentedConfigurationNode commented) {
      return commented;
    }
    throw new IllegalStateException("Expected a commented configuration node");
  }

  private String describe(CommentedConfigurationNode node) {
    if (node.isMap()) {
      return "map";
    }
    if (node.isList()) {
      return "list";
    }
    return "scalar";
  }
}
