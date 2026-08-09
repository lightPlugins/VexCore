package dev.vexsoft.core.common.service.localization;

import dev.vexsoft.core.api.service.registry.ServiceOwner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.api.configuration.ConfigurationOwner;
import dev.vexsoft.core.api.service.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.registry.ServiceReference;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.configuration.VexConfigurationService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VexThemeColorServiceTest {

  @TempDir
  private Path directory;

  @Test
  void loadsThemeTagsAndReloadsExternalChanges() throws Exception {
    TestOwner owner = new TestOwner(directory, "red:\n  - '#123456'\n  - '#654321'\n");
    VexThemeColorService themes = new VexThemeColorService(new TestServices(owner));

    assertEquals(TextColor.color(0x123456), themes.requireColor("TAILWIND", "RED", 1));
    assertEquals(
        TextColor.color(0x654321),
        themes.deserialize("<tailwind:red>Text</tailwind:red>").color()
    );
    assertEquals(
        TextColor.color(0x123456),
        themes.deserialize("<tailwind:red:1>Text</tailwind:red:1>").color()
    );

    Files.writeString(
        directory.resolve("themes/tailwind.yml"),
        "red:\n  - '#abcdef'\n  - '#654321'\n"
    );
    themes.reload();

    assertEquals(TextColor.color(0xabcdef), themes.requireColor("tailwind", "red", 1));
  }

  @Test
  void keepsPreviousSnapshotWhenReloadedColorIsInvalid() throws Exception {
    TestOwner owner = new TestOwner(directory, "red:\n  - '#123456'\n");
    VexThemeColorService themes = new VexThemeColorService(new TestServices(owner));
    Files.writeString(directory.resolve("themes/tailwind.yml"), "red:\n  - nope\n");

    assertThrows(IllegalArgumentException.class, themes::reload);
    assertEquals(TextColor.color(0x123456), themes.requireColor("tailwind", "red", 1));
  }

  private static final class TestOwner implements ConfigurationOwner {

    private final Path directory;
    private final byte[] defaults;

    private TestOwner(final Path directory, final String defaults) {
      this.directory = directory;
      this.defaults = defaults.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Path getConfigurationDirectory() {
      return directory;
    }

    @Override
    public Optional<InputStream> getConfigurationResource(final String resourcePath) {
      return "themes/tailwind.yml".equals(resourcePath)
          ? Optional.of(new ByteArrayInputStream(defaults))
          : Optional.empty();
    }

    @Override
    public void reportConfigurationWarning(final String message, final Throwable cause) {
    }

    @Override
    public String getServiceOwnerName() {
      return "TestPlugin";
    }
  }

  private static final class TestServices implements VexServiceRegistry {

    private final ConfigurationOwner owner;
    private final ConfigurationService configurations;

    private TestServices(final ConfigurationOwner owner) {
      this.owner = owner;
      configurations = new VexConfigurationService(this);
    }

    @Override
    public ConfigurationOwner getOwner() {
      return owner;
    }

    @Override
    public VexServiceRegistry scoped(final dev.vexsoft.core.api.service.registry.ServiceOwner childOwner) {
      throw new UnsupportedOperationException();
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
      return serviceType == ConfigurationService.class
          ? Optional.of(serviceType.cast(configurations))
          : Optional.empty();
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
      return find(serviceType).isPresent();
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
