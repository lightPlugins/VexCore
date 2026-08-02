package dev.vexsoft.core.localization;

import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Value;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

final class PluginLocalizationCache {

  private static final String RESOURCE_ROOT = "languages/";

  private final LocalizationOwner owner;
  private volatile Map<LanguageKey, Map<String, MessageTemplate>> messages = Map.of();

  PluginLocalizationCache(final LocalizationOwner owner) {
    this.owner = Objects.requireNonNull(owner, "owner");
    reload();
  }

  public synchronized void reload() {
    Map<LanguageKey, Map<String, MessageTemplate>> loaded = new LinkedHashMap<>();
    loadBundled(loaded);
    loadExternal(loaded);
    if (!loaded.containsKey(LanguageKey.EN_EN)) {
      throw new IllegalStateException(
          "Missing default language en_EN for " + owner.getServiceOwnerName()
      );
    }

    Map<LanguageKey, Map<String, MessageTemplate>> immutable = new LinkedHashMap<>();
    loaded.forEach((language, values) -> immutable.put(language, Map.copyOf(values)));
    messages = Map.copyOf(immutable);
  }

  public MessageTemplate find(final LanguageKey language, final String key) {
    Map<String, MessageTemplate> selected = messages.get(language);
    MessageTemplate value = selected == null ? null : selected.get(key);
    if (value != null || language.equals(LanguageKey.EN_EN)) {
      return value;
    }
    return messages.getOrDefault(LanguageKey.EN_EN, Map.of()).get(key);
  }

  public Collection<LanguageKey> getLanguages() {
    return messages.keySet().stream().sorted().toList();
  }

  private void loadBundled(final Map<LanguageKey, Map<String, MessageTemplate>> target) {
    owner.getLocalizationResources().stream()
        .filter(resource -> resource.startsWith(RESOURCE_ROOT) && resource.endsWith(".yml"))
        .sorted()
        .forEach(resource -> {
          ResourceLocation location = location(resource.substring(RESOURCE_ROOT.length()));
          copyIfMissing(resource, location);
          try (InputStream input = owner.getLocalizationResource(resource).orElseThrow(
              () -> new IllegalStateException("Missing bundled language resource " + resource)
          )) {
            loadFile(target, location, input, false, resource);
          } catch (IOException exception) {
            throw new IllegalStateException("Unable to close language resource " + resource, exception);
          }
        });
  }

  private void loadExternal(final Map<LanguageKey, Map<String, MessageTemplate>> target) {
    Path directory = owner.getLocalizationDirectory().toAbsolutePath().normalize();
    if (Files.notExists(directory)) {
      return;
    }
    try (var paths = Files.walk(directory)) {
      paths.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".yml"))
          .sorted(Comparator.comparing(Path::toString))
          .forEach(path -> {
            ResourceLocation location = location(directory.relativize(path).toString().replace('\\', '/'));
            try (InputStream input = Files.newInputStream(path)) {
              loadFile(target, location, input, true, path.toString());
            } catch (IOException exception) {
              owner.reportLocalizationWarning("Unable to load language file " + path, exception);
            }
          });
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to scan language directory " + directory, exception);
    }
  }

  private void loadFile(
      final Map<LanguageKey, Map<String, MessageTemplate>> target,
      final ResourceLocation location,
      final InputStream input,
      final boolean overwrite,
      final String source
  ) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      ConfigurationNode root = YamlConfigurationLoader.builder()
          .source(() -> reader)
          .build()
          .load();
      Map<String, MessageTemplate> values = new LinkedHashMap<>();
      flatten(root, location.keyPrefix, values, source);
      Map<String, MessageTemplate> language = target.computeIfAbsent(
          location.language,
          ignored -> new LinkedHashMap<>()
      );
      for (Map.Entry<String, MessageTemplate> entry : values.entrySet()) {
        if (overwrite) {
          language.put(entry.getKey(), entry.getValue());
        } else if (language.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
          owner.reportLocalizationWarning(
              "Duplicate bundled localization key " + entry.getKey() + " in " + source,
              null
          );
        }
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to parse language file " + source, exception);
    }
  }

  private void flatten(
      final ConfigurationNode node,
      final String path,
      final Map<String, MessageTemplate> target,
      final String source
  ) {
    if (node.isMap()) {
      node.childrenMap().forEach((key, child) -> flatten(
          child,
          path.isBlank() ? String.valueOf(key) : path + "." + key,
          target,
          source
      ));
      return;
    }
    if (path.isBlank()) {
      owner.reportLocalizationWarning("Ignoring empty localization root in " + source, null);
      return;
    }
    if (node.isList()) {
      List<String> lines = new ArrayList<>();
      for (ConfigurationNode child : node.childrenList()) {
        if (!(child.raw() instanceof String value)) {
          owner.reportLocalizationWarning(
              "Ignoring non-string localization list at " + path + " in " + source,
              null
          );
          return;
        }
        lines.add(value);
      }
      if (lines.isEmpty()) {
        owner.reportLocalizationWarning("Ignoring empty localization list at " + path + " in " + source, null);
        return;
      }
      target.put(path, new MessageTemplate(List.copyOf(lines), true));
      return;
    }
    if (node.raw() instanceof String value) {
      target.put(path, new MessageTemplate(List.of(value), false));
      return;
    }
    owner.reportLocalizationWarning(
        "Ignoring localization value that is not a string or string list at " + path + " in " + source,
        null
    );
  }

  private void copyIfMissing(final String resource, final ResourceLocation location) {
    Path directory = owner.getLocalizationDirectory().toAbsolutePath().normalize();
    Path target = directory.resolve(location.relativePath).normalize();
    if (!target.startsWith(directory)) {
      throw new IllegalArgumentException("Language resource escapes owner directory: " + resource);
    }
    if (Files.exists(target)) {
      return;
    }
    try (InputStream input = owner.getLocalizationResource(resource).orElseThrow()) {
      Files.createDirectories(target.getParent());
      Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to copy bundled language file " + resource, exception);
    }
  }

  private ResourceLocation location(final String relativePath) {
    Path relative = Path.of(relativePath).normalize();
    if (relative.isAbsolute() || relative.getNameCount() < 2 || relative.startsWith("..")) {
      throw new IllegalArgumentException("Invalid language resource path: " + relativePath);
    }
    LanguageKey language = LanguageKey.of(relative.getName(0).toString());
    Path yamlPath = relative.subpath(1, relative.getNameCount());
    String file = yamlPath.toString().replace('\\', '/');
    if (!file.endsWith(".yml")) {
      throw new IllegalArgumentException("Language resource must be YAML: " + relativePath);
    }
    String keyPrefix = file.substring(0, file.length() - 4).replace('/', '.');
    return new ResourceLocation(language, relative, keyPrefix);
  }

  @Value
  private static class ResourceLocation {
    LanguageKey language;
    Path relativePath;
    String keyPrefix;
  }
}
