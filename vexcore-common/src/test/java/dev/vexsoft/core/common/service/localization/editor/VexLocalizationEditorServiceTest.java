package dev.vexsoft.core.common.service.localization.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.common.service.localization.LocalizationRegistryService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VexLocalizationEditorServiceTest {

  @TempDir
  private Path temporaryDirectory;

  @Test
  void browsesDirectoriesAndRootFilesAndCreatesLanguageOverrides() throws Exception {
    TestOwner owner = new TestOwner(temporaryDirectory);
    TestRegistry registry = new TestRegistry(owner);
    VexLocalizationEditorService service = new VexLocalizationEditorService(registry);

    Collection<LocalizationBrowserNode> root = service.browse(
        "TestPlugin",
        LanguageKey.of("de_DE"),
        Path.of("")
    );
    assertTrue(root.stream().anyMatch(node -> node.directory() && node.name().equals("menus")));
    assertTrue(root.stream().anyMatch(node -> !node.directory() && node.name().equals("general.yml")));

    List<LocalizationEntryView> inherited = List.copyOf(service.getEntries(
        "TestPlugin",
        LanguageKey.of("de_DE"),
        Path.of("menus", "warps.yml")
    ));
    assertEquals("title", inherited.getFirst().key());
    assertTrue(inherited.getFirst().inherited());

    service.update(
        "TestPlugin",
        LanguageKey.of("de_DE"),
        Path.of("menus", "warps.yml"),
        "title",
        LocalizationValue.text("<red>Warps")
    );

    Path written = temporaryDirectory.resolve("de_DE/menus/warps.yml");
    assertTrue(Files.exists(written));
    assertTrue(Files.readString(written).contains("<red>Warps"));
    assertEquals(1, registry.reloads.get());
    LocalizationEntryView local = service.getEntries(
        "TestPlugin",
        LanguageKey.of("de_DE"),
        Path.of("menus", "warps.yml")
    ).iterator().next();
    assertFalse(local.inherited());
  }

  private static final class TestRegistry implements LocalizationRegistryService {

    private final TestOwner owner;
    private final AtomicInteger reloads = new AtomicInteger();

    private TestRegistry(final TestOwner owner) {
      this.owner = owner;
    }

    @Override
    public void register(final LocalizationOwner owner) {
    }

    @Override
    public void unregister(final LocalizationOwner owner) {
    }

    @Override
    public LocalizedMessage resolve(
        final LocalizationOwner owner,
        final LanguageKey language,
        final String key,
        final Map<String, String> replacements
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public LocalizedMessage resolve(
        final String ownerName,
        final LanguageKey language,
        final String key,
        final Map<String, String> replacements
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Collection<LanguageKey> getLanguages(final String ownerName) {
      return List.of(LanguageKey.EN_EN);
    }

    @Override
    public Collection<LocalizationOwner> getOwners() {
      return List.of(owner);
    }

    @Override
    public void reload(final String ownerName) {
      reloads.incrementAndGet();
    }

    @Override
    public void reload(final LocalizationOwner owner) {
    }

    @Override
    public void reloadAll() {
    }
  }

  private static final class TestOwner implements LocalizationOwner {

    private final Path directory;
    private final Map<String, String> resources = Map.of(
        "languages/en_EN/general.yml", "hello: '<green>Hello'\n",
        "languages/en_EN/menus/warps.yml", "title: '<blue>Warps'\n"
    );

    private TestOwner(final Path directory) {
      this.directory = directory;
    }

    @Override
    public Path getLocalizationDirectory() {
      return directory;
    }

    @Override
    public Collection<String> getLocalizationResources() {
      return resources.keySet();
    }

    @Override
    public Optional<InputStream> getLocalizationResource(final String resourcePath) {
      String value = resources.get(resourcePath);
      return value == null
          ? Optional.empty()
          : Optional.of(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String getMessagePrefixKey() {
      return "general.prefix";
    }

    @Override
    public void reportLocalizationWarning(final String message, final Throwable cause) {
    }

    @Override
    public String getServiceOwnerName() {
      return "TestPlugin";
    }
  }
}
