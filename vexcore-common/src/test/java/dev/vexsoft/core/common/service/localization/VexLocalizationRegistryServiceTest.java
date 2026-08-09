package dev.vexsoft.core.common.service.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.ServiceReference;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.localization.ThemeColorService;
import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.common.service.cache.VexCacheService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
    java.nio.file.Files.createDirectories(java.util.Objects.requireNonNull(external.getParent()));
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

  @Test
  void ignoresInvalidExternalLanguageFoldersWithOneReadableWarning() throws Exception {
    TestOwner owner = new TestOwner(directory, Map.of(
        "languages/en_EN/messages.yml",
        "message: \"Valid\"\n"
    ));
    Path invalid = directory.resolve("some_language");
    java.nio.file.Files.createDirectories(invalid);
    java.nio.file.Files.writeString(invalid.resolve("first.yml"), "message: \"Invalid\"\n");
    java.nio.file.Files.writeString(invalid.resolve("second.yml"), "message: \"Invalid\"\n");
    VexLocalizationRegistryService registry = new VexLocalizationRegistryService(new TestServices(owner));

    registry.register(owner);

    assertEquals(1, owner.warnings.size());
    assertTrue(owner.warnings.getFirst().contains("some_language"));
    assertEquals(
        "Valid",
        plain(registry.resolve(owner, LanguageKey.EN_EN, "messages.message", Map.of()).getComponent())
    );
  }

  @Test
  void cachesStaticMessagesUntilTheOwnerIsReloaded() {
    TestOwner owner = new TestOwner(directory, Map.of(
        "languages/en_EN/messages.yml",
        "message: \"Cached\"\n"
    ));
    VexLocalizationRegistryService registry = new VexLocalizationRegistryService(
        new TestServices(owner)
    );
    registry.register(owner);

    LocalizedMessage first = registry.resolve(
        owner,
        LanguageKey.EN_EN,
        "messages.message",
        Map.of()
    );
    LocalizedMessage second = registry.resolve(
        owner,
        LanguageKey.EN_EN,
        "messages.message",
        Map.of()
    );

    assertSame(first, second);

    registry.reload(owner);
    LocalizedMessage reloaded = registry.resolve(
        owner,
        LanguageKey.EN_EN,
        "messages.message",
        Map.of()
    );

    assertNotSame(first, reloaded);
    assertEquals("Cached", plain(reloaded.getComponent()));
  }

  private String plain(final net.kyori.adventure.text.Component component) {
    return PlainTextComponentSerializer.plainText().serialize(component);
  }

  private static final class TestOwner implements LocalizationOwner {

    private final Path directory;
    private final Map<String, byte[]> resources = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();

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
      warnings.add(message);
    }

    @Override
    public String getServiceOwnerName() {
      return "TestPlugin";
    }
  }

  private static final class TestServices implements VexServiceRegistry {

    private final ServiceOwner owner;
    private final CacheService cache;
    private final ThemeColorService themeColors = new TestThemeColors();

    private TestServices(final ServiceOwner owner) {
      this.owner = owner;
      cache = new VexCacheService(this);
    }

    @Override
    public ServiceOwner getOwner() {
      return owner;
    }

    @Override
    public VexServiceRegistry scoped(final ServiceOwner childOwner) {
      return this;
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
      if (serviceType == CacheService.class) {
        return Optional.of(serviceType.cast(cache));
      }
      if (serviceType == ThemeColorService.class) {
        return Optional.of(serviceType.cast(themeColors));
      }
      return Optional.empty();
    }

    @Override
    public <T extends VexService> T require(final Class<T> serviceType) {
      return find(serviceType).orElseThrow();
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

  private static final class TestThemeColors implements ThemeColorService {

    @Override
    public Optional<TextColor> findColor(
        final String theme,
        final String color,
        final int shade
    ) {
      return Optional.empty();
    }

    @Override
    public TextColor requireColor(final String theme, final String color, final int shade) {
      throw new IllegalArgumentException("Unknown theme color: " + theme + ":" + color);
    }

    @Override
    public Map<String, Map<String, List<TextColor>>> getThemes() {
      return Map.of();
    }

    @Override
    public Component deserialize(final String input) {
      return MiniMessage.miniMessage().deserialize(input);
    }

    @Override
    public void reload() {
    }
  }
}
