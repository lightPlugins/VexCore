package dev.vexsoft.core.common.service.localization;



import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vexsoft.core.api.localization.Language;
import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.placeholder.PlaceholderService;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.ServiceReference;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.placeholder.PlaceholderContext;
import dev.vexsoft.core.placeholder.VexPlaceholder;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class VexLocalizedMessageServiceTest {

  @Test
  void sendsPlayerLanguageAndExplicitAudienceMessagesThroughOneImplementation() {
    Language german = new Language(
        LanguageKey.of("de_DE"),
        Component.text("Deutsch"),
        false
    );
    List<Component> received = new ArrayList<>();
    Audience audience = recordingAudience(received);
    TestServices services = new TestServices();
    VexLocalizedMessageService messages = new VexLocalizedMessageService(services);
    VexPlayer player = new VexPlayer(
        UUID.randomUUID(),
        "Alex",
        type -> type == LanguageContainer.class ? 0 : -1
    );
    player.installContainer(0, LanguageContainer.class, new LanguageContainer() {
      @Override
      public Language getLanguage() {
        return german;
      }

      @Override
      public void setLanguage(final LanguageKey language) {
        throw new UnsupportedOperationException();
      }
    });
    player.bindPlatformPlayer(audience);

    messages.send(player, "message", true, Map.of("name", "Alex"));
    messages.send(audience, LanguageKey.EN_EN, "message", false, Map.of());

    List<String> rendered = received.stream()
        .map(PlainTextComponentSerializer.plainText()::serialize)
        .toList();
    assertEquals(List.of("[VexCore] Hello Alex", "Hello {name}"), rendered);
  }

  private static Audience recordingAudience(final List<Component> received) {
    return (Audience) Proxy.newProxyInstance(
        Audience.class.getClassLoader(),
        new Class<?>[]{Audience.class},
        (proxy, method, arguments) -> {
          if (method.getName().equals("sendMessage") && arguments != null) {
            for (Object argument : arguments) {
              if (argument instanceof Component component) {
                received.add(component);
                break;
              }
            }
          }
          return null;
        }
    );
  }

  private static final class TestServices implements VexServiceRegistry, LocalizationOwner {

    private final LocalizationService localization = new LocalizationService() {
      @Override
      public LocalizedMessage resolve(
          final LanguageKey language,
          final String key,
          final Map<String, String> replacements
      ) {
        if (key.equals("general.prefix")) {
          return LocalizedMessage.single(Component.text("[VexCore] "));
        }
        return LocalizedMessage.single(Component.text(
            replacements.containsKey("name") ? "Hello " + replacements.get("name") : "Hello {name}"
        ));
      }

      @Override
      public void reload() {
      }
    };
    private final PlaceholderService placeholders = new PlaceholderService() {
      @Override
      public <T extends VexPlaceholder> T register(final Class<T> placeholderType) {
        throw new UnsupportedOperationException();
      }

      @Override
      public String resolve(final VexPlayer player, final String input) {
        return input;
      }

      @Override
      public String resolve(final PlaceholderContext context, final String input) {
        return input;
      }

      @Override
      public Component resolve(final VexPlayer player, final Component component) {
        return component;
      }

      @Override
      public void clear() { }
    };

    @Override
    public ServiceOwner getOwner() {
      return this;
    }

    @Override
    public VexServiceRegistry scoped(final ServiceOwner owner) {
      return this;
    }

    @Override
    public <T extends VexService> Optional<T> find(final Class<T> serviceType) {
      if (serviceType == LocalizationService.class) {
        return Optional.of(serviceType.cast(localization));
      }
      if (serviceType == PlaceholderService.class) {
        return Optional.of(serviceType.cast(placeholders));
      }
      return Optional.empty();
    }

    @Override
    public <T extends VexService> T require(final Class<T> serviceType) {
      return find(serviceType).orElseThrow();
    }

    @Override
    public String getServiceOwnerName() {
      return "VexCoreTest";
    }

    @Override
    public Path getLocalizationDirectory() {
      return Path.of("languages");
    }

    @Override
    public Collection<String> getLocalizationResources() {
      return List.of();
    }

    @Override
    public Optional<InputStream> getLocalizationResource(final String resourcePath) {
      return Optional.empty();
    }

    @Override
    public String getMessagePrefixKey() {
      return "general.prefix";
    }

    @Override
    public void reportLocalizationWarning(final String message, final Throwable cause) {
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
