package dev.vexsoft.core.common.service.localization.editor;

import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.localization.LocalizationRegistryService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/** Default filesystem-backed localization editor. */
@Dependencies(LocalizationRegistryService.class)
public final class VexLocalizationEditorService implements LocalizationEditorService {

  private static final String RESOURCE_ROOT = "languages/";
  private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern(
      "yyyy-MM-dd_HH-mm-ss_SSS"
  );

  private final LocalizationRegistryService registry;

  public VexLocalizationEditorService(final VexServiceRegistry services) {
    registry = Objects.requireNonNull(services, "services")
        .require(LocalizationRegistryService.class);
  }

  VexLocalizationEditorService(final LocalizationRegistryService registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  @Override
  public Collection<LocalizationOwner> getOwners() {
    return registry.getOwners();
  }

  @Override
  public Collection<LanguageKey> getLanguages(final String ownerName) {
    LocalizationOwner owner = owner(ownerName);
    Set<LanguageKey> languages = new LinkedHashSet<>(registry.getLanguages(ownerName));
    resources(owner).keySet().forEach(path -> languages.add(language(path)));
    languages.add(LanguageKey.EN_EN);
    return languages.stream().sorted().toList();
  }

  @Override
  public Collection<LocalizationBrowserNode> browse(
      final String ownerName,
      final LanguageKey language,
      final Path relativeDirectory
  ) {
    LocalizationOwner owner = owner(ownerName);
    Path directory = safeRelativeDirectory(relativeDirectory);
    Map<Path, SourceFile> selected = files(owner, language);
    Map<Path, SourceFile> defaults = language.equals(LanguageKey.EN_EN)
        ? Map.of()
        : files(owner, LanguageKey.EN_EN);
    Set<Path> candidates = new LinkedHashSet<>(defaults.keySet());
    candidates.addAll(selected.keySet());
    Map<String, LocalizationBrowserNode> nodes = new LinkedHashMap<>();
    for (Path file : candidates) {
      if (!isDirectOrDescendant(directory, file)) {
        continue;
      }
      Path remainder = directory.toString().isEmpty()
          ? file
          : directory.relativize(file);
      if (remainder.getNameCount() == 0) {
        continue;
      }
      Path child = directory.resolve(remainder.getName(0));
      boolean childDirectory = remainder.getNameCount() > 1;
      String name = remainder.getName(0).toString();
      boolean inherited = childDirectory
          ? selected.keySet().stream().noneMatch(candidate -> isDirectOrDescendant(child, candidate))
          : !selected.containsKey(child);
      nodes.putIfAbsent(
          (childDirectory ? "0:" : "1:") + name.toLowerCase(Locale.ROOT),
          new LocalizationBrowserNode(name, child, childDirectory, inherited)
      );
    }
    return nodes.values().stream()
        .sorted(Comparator.comparing(LocalizationBrowserNode::directory).reversed()
            .thenComparing(LocalizationBrowserNode::name, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  @Override
  public Collection<LocalizationEntryView> getEntries(
      final String ownerName,
      final LanguageKey language,
      final Path relativeFile
  ) {
    LocalizationOwner owner = owner(ownerName);
    Path file = safeRelativeFile(relativeFile);
    Map<String, LocalizationValue> selected = read(files(owner, language).get(file));
    Map<String, LocalizationValue> defaults = language.equals(LanguageKey.EN_EN)
        ? Map.of()
        : read(files(owner, LanguageKey.EN_EN).get(file));
    Set<String> keys = new LinkedHashSet<>(defaults.keySet());
    keys.addAll(selected.keySet());
    return keys.stream().sorted().map(key -> {
      LocalizationValue local = selected.get(key);
      return new LocalizationEntryView(
          key,
          local == null ? defaults.get(key) : local,
          local == null
      );
    }).toList();
  }

  @Override
  public synchronized void update(
      final String ownerName,
      final LanguageKey language,
      final Path relativeFile,
      final String key,
      final LocalizationValue value
  ) {
    LocalizationOwner owner = owner(ownerName);
    Path file = safeRelativeFile(relativeFile);
    String checkedKey = key(key);
    LocalizationValue checkedValue = Objects.requireNonNull(value, "value");
    Path languageRoot = owner.getLocalizationDirectory().toAbsolutePath().normalize()
        .resolve(language.getValue())
        .normalize();
    Path target = languageRoot.resolve(file).normalize();
    if (!target.startsWith(languageRoot)) {
      throw new IllegalArgumentException("Localization file escapes its language directory");
    }
    try {
      rejectSymbolicLinks(owner.getLocalizationDirectory().toAbsolutePath().normalize(), target);
      Files.createDirectories(requireParent(target));
      CommentedConfigurationNode root = Files.exists(target)
          ? loader(target).load()
          : CommentedConfigurationNode.root();
      ConfigurationNode node = root;
      for (String part : checkedKey.split("\\.")) {
        node = node.node(part);
      }
      node.raw(checkedValue.list() ? new ArrayList<>(checkedValue.lines()) : checkedValue.lines().getFirst());
      Path temporary = Files.createTempFile(
          requireParent(target),
          fileName(target),
          ".tmp"
      );
      Path backup = null;
      boolean existed = Files.exists(target);
      try {
        loader(temporary).save(root);
        validate(temporary);
        backup = backup(owner, language, file, target);
        replace(temporary, target);
        try {
          registry.reload(ownerName);
        } catch (RuntimeException exception) {
          rollback(target, backup, existed);
          registry.reload(ownerName);
          throw exception;
        }
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to update localization file " + target, exception);
    }
  }

  private Map<Path, SourceFile> files(
      final LocalizationOwner owner,
      final LanguageKey language
  ) {
    Map<Path, SourceFile> files = new LinkedHashMap<>();
    resources(owner).forEach((path, source) -> {
      if (language(path).equals(language)) {
        files.put(path.subpath(1, path.getNameCount()), source);
      }
    });
    Path languageDirectory = owner.getLocalizationDirectory().toAbsolutePath().normalize()
        .resolve(language.getValue())
        .normalize();
    if (Files.isDirectory(languageDirectory)) {
      try (var paths = Files.walk(languageDirectory)) {
        paths.filter(Files::isRegularFile)
            .filter(path -> fileName(path).endsWith(".yml"))
            .filter(path -> !path.startsWith(languageDirectory.resolve(".backups")))
            .sorted()
            .forEach(path -> files.put(
                languageDirectory.relativize(path),
                SourceFile.external(path)
            ));
      } catch (IOException exception) {
        throw new IllegalStateException("Unable to scan " + languageDirectory, exception);
      }
    }
    return Map.copyOf(files);
  }

  private Map<Path, SourceFile> resources(final LocalizationOwner owner) {
    Map<Path, SourceFile> resources = new LinkedHashMap<>();
    owner.getLocalizationResources().stream()
        .filter(resource -> resource.startsWith(RESOURCE_ROOT) && resource.endsWith(".yml"))
        .sorted()
        .forEach(resource -> {
          Path relative = safeResourcePath(resource.substring(RESOURCE_ROOT.length()));
          resources.put(relative, SourceFile.resource(owner, resource));
        });
    return resources;
  }

  private Map<String, LocalizationValue> read(final SourceFile source) {
    if (source == null) {
      return Map.of();
    }
    try (InputStream input = source.open();
         BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      ConfigurationNode root = YamlConfigurationLoader.builder().source(() -> reader).build().load();
      Map<String, LocalizationValue> values = new LinkedHashMap<>();
      flatten(root, "", values);
      return Map.copyOf(values);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to read localization source", exception);
    }
  }

  private void flatten(
      final ConfigurationNode node,
      final String path,
      final Map<String, LocalizationValue> target
  ) {
    if (node.isMap()) {
      node.childrenMap().forEach((key, child) -> flatten(
          child,
          path.isEmpty() ? String.valueOf(key) : path + '.' + key,
          target
      ));
    } else if (node.isList()) {
      List<String> lines = node.childrenList().stream()
          .map(ConfigurationNode::raw)
          .filter(String.class::isInstance)
          .map(String.class::cast)
          .toList();
      if (!lines.isEmpty()) {
        target.put(path, LocalizationValue.lines(lines));
      }
    } else if (node.raw() instanceof String text) {
      target.put(path, LocalizationValue.text(text));
    }
  }

  private LocalizationOwner owner(final String ownerName) {
    return registry.getOwners().stream()
        .filter(owner -> owner.getServiceOwnerName().equalsIgnoreCase(ownerName))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            "Unknown localization owner: " + ownerName
        ));
  }

  private Path backup(
      final LocalizationOwner owner,
      final LanguageKey language,
      final Path relativeFile,
      final Path target
  ) throws IOException {
    if (Files.notExists(target)) {
      return null;
    }
    Path backup = owner.getLocalizationDirectory().resolveSibling("localization-backups")
        .resolve(BACKUP_TIME.format(LocalDateTime.now()))
        .resolve(language.getValue())
        .resolve(relativeFile)
        .normalize();
    Files.createDirectories(requireParent(backup));
    Files.copy(target, backup, StandardCopyOption.COPY_ATTRIBUTES);
    return backup;
  }

  private void rollback(final Path target, final Path backup, final boolean existed) {
    try {
      if (existed && backup != null) {
        Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
      } else {
        Files.deleteIfExists(target);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to roll back localization file " + target, exception);
    }
  }

  private void replace(final Path source, final Path target) throws IOException {
    try {
      Files.move(
          source,
          target,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING
      );
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void validate(final Path file) throws IOException {
    loader(file).load();
  }

  private void rejectSymbolicLinks(final Path root, final Path target) throws IOException {
    Path current = root;
    if (Files.isSymbolicLink(current)) {
      throw new IOException("Localization directory must not be a symbolic link: " + current);
    }
    Path relative = root.relativize(target);
    for (Path part : relative) {
      current = current.resolve(part);
      if (Files.exists(current) && Files.isSymbolicLink(current)) {
        throw new IOException("Localization path must not contain symbolic links: " + current);
      }
    }
  }

  private YamlConfigurationLoader loader(final Path file) {
    return YamlConfigurationLoader.builder()
        .path(file)
        .indent(2)
        .nodeStyle(NodeStyle.BLOCK)
        .build();
  }

  private Path safeResourcePath(final String value) {
    Path path = Path.of(value).normalize();
    if (path.isAbsolute() || path.getNameCount() < 2 || path.startsWith("..")) {
      throw new IllegalArgumentException("Invalid localization resource path: " + value);
    }
    language(path);
    safeRelativeFile(path.subpath(1, path.getNameCount()));
    return path;
  }

  private Path safeRelativeDirectory(final Path value) {
    Path path = Objects.requireNonNull(value, "relativeDirectory").normalize();
    if (path.toString().equals(".")) {
      return Path.of("");
    }
    if (path.isAbsolute() || path.startsWith("..")) {
      throw new IllegalArgumentException("Invalid localization directory: " + value);
    }
    return path;
  }

  private Path safeRelativeFile(final Path value) {
    Path path = Objects.requireNonNull(value, "relativeFile").normalize();
    if (path.isAbsolute() || path.startsWith("..") || !path.toString().endsWith(".yml")) {
      throw new IllegalArgumentException("Invalid localization file: " + value);
    }
    return path;
  }

  private String key(final String value) {
    String key = Objects.requireNonNull(value, "key").trim();
    if (!key.matches("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*")) {
      throw new IllegalArgumentException("Invalid localization key: " + value);
    }
    return key;
  }

  private LanguageKey language(final Path resource) {
    return LanguageKey.of(resource.getName(0).toString());
  }

  private boolean isDirectOrDescendant(final Path directory, final Path file) {
    return directory.toString().isEmpty() || file.startsWith(directory);
  }

  private Path requireParent(final Path path) {
    Path parent = path.getParent();
    if (parent == null) {
      throw new IllegalArgumentException("Path has no parent: " + path);
    }
    return parent;
  }

  private String fileName(final Path path) {
    Path name = path.getFileName();
    if (name == null) {
      throw new IllegalArgumentException("Path has no file name: " + path);
    }
    return name.toString();
  }

  private record SourceFile(Path file, LocalizationOwner owner, String resource) {

    static SourceFile external(final Path file) {
      return new SourceFile(file, null, null);
    }

    static SourceFile resource(final LocalizationOwner owner, final String resource) {
      return new SourceFile(null, owner, resource);
    }

    InputStream open() throws IOException {
      if (file != null) {
        return Files.newInputStream(file);
      }
      return owner.getLocalizationResource(resource).orElseThrow(
          () -> new IOException("Missing localization resource " + resource)
      );
    }
  }
}
