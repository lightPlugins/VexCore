package dev.vexsoft.core.localization;

import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.cache.CacheService;
import dev.vexsoft.core.cache.VexCache;
import dev.vexsoft.core.cache.VexCacheOptions;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import lombok.Value;

@Dependencies(CacheService.class)
public final class VexLocalizationRegistryService implements LocalizationRegistryService {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_.-]+)}");

  private final Map<String, Registration> registrations = new ConcurrentHashMap<>();
  private final MiniMessage miniMessage = MiniMessage.miniMessage();
  private final VexCache<StaticMessageKey, LocalizedMessage> staticMessages;

  public VexLocalizationRegistryService(final VexServiceRegistry services) {
    staticMessages = Objects.requireNonNull(services, "services")
        .require(CacheService.class)
        .create(
            "localization-static-messages",
            VexCacheOptions.builder()
                .maximumSize(20_000L)
                .expireAfterAccess(Duration.ofMinutes(30))
                .build()
        );
  }

  @Override
  public void register(final LocalizationOwner owner) {
    Objects.requireNonNull(owner, "owner");
    String ownerName = normalizeOwner(owner.getServiceOwnerName());
    Registration registration = new Registration(owner, new PluginLocalizationCache(owner));
    Registration existing = registrations.putIfAbsent(ownerName, registration);
    if (existing != null && existing.owner != owner) {
      throw new IllegalStateException("Localization owner is already registered: " + ownerName);
    }
    staticMessages.invalidateAll();
  }

  @Override
  public void unregister(final LocalizationOwner owner) {
    Objects.requireNonNull(owner, "owner");
    registrations.computeIfPresent(normalizeOwner(owner.getServiceOwnerName()), (key, registration) ->
        registration.owner == owner ? null : registration
    );
    staticMessages.invalidateAll();
  }

  @Override
  public LocalizedMessage resolve(
      final LocalizationOwner owner,
      final LanguageKey language,
      final String key,
      final Map<String, String> replacements
  ) {
    Registration registration = require(normalizeOwner(owner.getServiceOwnerName()));
    if (registration.owner != owner) {
      throw new IllegalStateException("Localization owner instance is no longer registered: " + owner.getServiceOwnerName());
    }
    return render(registration, language, key, replacements);
  }

  @Override
  public LocalizedMessage resolve(
      final String ownerName,
      final LanguageKey language,
      final String key,
      final Map<String, String> replacements
  ) {
    return render(require(normalizeOwner(ownerName)), language, key, replacements);
  }

  @Override
  public Collection<LanguageKey> getLanguages(final String ownerName) {
    return require(normalizeOwner(ownerName)).cache.getLanguages();
  }

  @Override
  public void reload(final LocalizationOwner owner) {
    Registration registration = require(normalizeOwner(owner.getServiceOwnerName()));
    if (registration.owner != owner) {
      throw new IllegalStateException("Localization owner instance is no longer registered: " + owner.getServiceOwnerName());
    }
    registration.cache.reload();
    staticMessages.invalidateAll();
  }

  @Override
  public void reloadAll() {
    try {
      registrations.values().forEach(registration -> registration.cache.reload());
    } finally {
      staticMessages.invalidateAll();
    }
  }

  private LocalizedMessage render(
      final Registration registration,
      final LanguageKey language,
      final String key,
      final Map<String, String> replacements
  ) {
    Objects.requireNonNull(language, "language");
    String checkedKey = Objects.requireNonNull(key, "key").trim();
    Map<String, String> checkedReplacements = Objects.requireNonNull(
        replacements,
        "replacements"
    );
    if (checkedReplacements.isEmpty()) {
      StaticMessageKey cacheKey = new StaticMessageKey(
          normalizeOwner(registration.owner.getServiceOwnerName()),
          language,
          checkedKey
      );
      return staticMessages.get(
          cacheKey,
          ignored -> renderTemplate(registration, language, checkedKey, checkedReplacements)
      );
    }
    return renderTemplate(registration, language, checkedKey, checkedReplacements);
  }

  private LocalizedMessage renderTemplate(
      final Registration registration,
      final LanguageKey language,
      final String key,
      final Map<String, String> replacements
  ) {
    MessageTemplate template = registration.cache.find(language, key);
    if (template == null) {
      return LocalizedMessage.single(Component.text(
          "Missing localization: " + registration.owner.getServiceOwnerName() + ":" + key,
          NamedTextColor.RED
      ));
    }
    List<Component> components = template.getLines().stream()
        .map(line -> miniMessage.deserialize(replace(line, replacements)))
        .toList();
    return template.isList()
        ? LocalizedMessage.list(components)
        : LocalizedMessage.single(components.getFirst());
  }

  private String replace(final String template, final Map<String, String> replacements) {
    Map<String, String> checkedReplacements = Objects.requireNonNull(replacements, "replacements");
    for (Map.Entry<String, String> replacement : checkedReplacements.entrySet()) {
      String key = Objects.requireNonNull(replacement.getKey(), "replacement key");
      if (!key.matches("[A-Za-z0-9_.-]+")) {
        throw new IllegalArgumentException("Invalid replacement key: " + key);
      }
      Objects.requireNonNull(replacement.getValue(), "replacement value");
    }

    Matcher matcher = PLACEHOLDER.matcher(template);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String value = checkedReplacements.get(matcher.group(1));
      String replacement = value == null
          ? matcher.group()
          : miniMessage.escapeTags(value);
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private Registration require(final String ownerName) {
    Registration registration = registrations.get(ownerName);
    if (registration == null) {
      throw new IllegalStateException("Localization owner is not registered: " + ownerName);
    }
    return registration;
  }

  private String normalizeOwner(final String ownerName) {
    return Objects.requireNonNull(ownerName, "ownerName").trim().toLowerCase(Locale.ROOT);
  }

  @Value
  private static class Registration {
    LocalizationOwner owner;
    PluginLocalizationCache cache;
  }

  @Value
  private static class StaticMessageKey {
    String ownerName;
    LanguageKey language;
    String messageKey;
  }
}
