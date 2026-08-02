package dev.vexsoft.core.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.ServiceReference;
import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VexLocalizationRegistryServiceTest {

  @TempDir
  private Path directory;

  @Test
  void detectsStringsAndListsAndEscapesReplacements() {
    TestOwner owner = new TestOwner(directory, Map.of(
        "languages/en_EN/messages.yml",
        "single: \"Hello {name}\"\nlist:\n  - \"First {name}\"\n  - \"Second\"\n"
    ));
    VexLocalizationRegistryService registry = new VexLocalizationRegistryService(new TestServices(owner));
    registry.register(owner);

    LocalizedMessage single = registry.resolve(
        owner,
        LanguageKey.EN_EN,
        "messages.single",
        Map.of("name", "<red>Alex")
    );
    LocalizedMessage list = registry.resolve(
        owner,
        LanguageKey.EN_EN,
        "messages.list",
        Map.of("name", "Alex")
    );

    assertFalse(single.isList());
    assertEquals("Hello <red>Alex", plain(single.getComponent()));
    assertTrue(list.isList());
    assertEquals(List.of("First Alex", "Second"), list.getLines().stream().map(this::plain).toList());

    LocalizedMessage nonCascading = registry.resolve(
        owner,
        LanguageKey.EN_EN,
        "messages.single",
        Map.of("name", "{other}", "other", "changed")
    );
    assertEquals("Hello {other}", plain(nonCascading.getComponent()));
  }

  @Test
  void fallsBackToEnglishAndKeepsBundledKeysBelowExternalOverrides() throws Exception {
    TestOwner owner = new TestOwner(directory, Map.of(
        "languages/en_EN/messages.yml",
        "existing: \"Bundled\"\nadded: \"New bundled key\"\n",
        "languages/en_EN/general.yml",
        "prefix: \"Prefix \"\n"
    ));
    Path external = directory.resolve("en_EN/messages.yml");
    java.nio.file.Files.createDirectories(external.getParent());
    java.nio.file.Files.writeString(external, "existing: \"External\"\n");
    VexLocalizationRegistryService registry = new VexLocalizationRegistryService(new TestServices(owner));
    registry.register(owner);

    assertEquals(
        "External",
        plain(registry.resolve(owner, LanguageKey.EN_EN, "messages.existing", Map.of()).getComponent())
    );
    assertEquals(
        "New bundled key",
        plain(registry.resolve(owner, LanguageKey.of("de_DE"), "messages.added", Map.of()).getComponent())
    );
  }

  private String plain(final net.kyori.adventure.text.Component component) {
    return PlainTextComponentSerializer.plainText().serialize(component);
  }

  private static final class TestOwner implements LocalizationOwner {

    private final Path directory;
    private final Map<String, byte[]> resources = new LinkedHashMap<>();

    private TestOwner(final Path directory, final Map<String, String> resources) {
      this.directory = directory;
      resources.forEach((key, value) -> this.resources.put(
          key,
          value.getBytes(StandardCharsets.UTF_8)
      ));
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
      byte[] value = resources.get(resourcePath);
      return value == null ? Optional.empty() : Optional.of(new ByteArrayInputStream(value));
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

  private static final class TestServices implements VexServiceRegistry {

    private final ServiceOwner owner;

    private TestServices(final ServiceOwner owner) {
      this.owner = owner;
    }

    @Override
    public ServiceOwner getOwner() {
      return owner;
    }

    @Override
    public <T extends VexService> void register(
        final Class<T> serviceType,
        final Class<? extends T> implementationType
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void registerQueuedServices() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T extends VexService> Optional<T> find(final Class<T> serviceType) {
      return Optional.empty();
    }

    @Override
    public <T extends VexService> T require(final Class<T> serviceType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T extends VexService> ServiceReference<T> reference(final Class<T> serviceType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAvailable(final Class<? extends VexService> serviceType) {
      return false;
    }

    @Override
    public void unregister(final Class<? extends VexService> serviceType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void unregisterOwnedServices() {
      throw new UnsupportedOperationException();
    }
  }
}
