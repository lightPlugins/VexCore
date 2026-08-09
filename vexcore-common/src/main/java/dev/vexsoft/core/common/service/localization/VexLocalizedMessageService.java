package dev.vexsoft.core.common.service.localization;


import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.service.localization.LocalizedMessageService;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.placeholder.PlaceholderService;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

/** Default platform-neutral localized-message sender. */
@Dependencies({LocalizationService.class, PlaceholderService.class})
public final class VexLocalizedMessageService implements LocalizedMessageService {

  private final LocalizationService localization;
  private final PlaceholderService placeholders;
  private final String prefixKey;

  public VexLocalizedMessageService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    if (!(checkedServices.getOwner() instanceof LocalizationOwner owner)) {
      throw new IllegalArgumentException("LocalizedMessageService owner must support localizations");
    }
    localization = checkedServices.require(LocalizationService.class);
    placeholders = checkedServices.require(PlaceholderService.class);
    prefixKey = Objects.requireNonNull(owner.getMessagePrefixKey(), "prefixKey");
  }

  @Override
  public void send(
      final VexPlayer player,
      final String key,
      final boolean withPrefix,
      final Map<String, String> replacements
  ) {
    VexPlayer checkedPlayer = Objects.requireNonNull(player, "player");
    LanguageKey language = checkedPlayer
        .getContainer(LanguageContainer.class)
        .getLanguage()
        .getKey();
    Audience audience = checkedPlayer.requirePlatformPlayer(Audience.class);
    LocalizedMessage message = localization.resolve(
        language,
        Objects.requireNonNull(key, "key"),
        Objects.requireNonNull(replacements, "replacements")
    );
    Component prefix = withPrefix
        ? placeholders.resolve(
            checkedPlayer,
            localization.resolve(language, prefixKey, Map.of()).getLines().getFirst()
        )
        : Component.empty();
    for (Component line : message.getLines()) {
      Component resolved = placeholders.resolve(checkedPlayer, line);
      audience.sendMessage(withPrefix ? prefix.append(resolved) : resolved);
    }
  }

  @Override
  public void send(
      final Audience audience,
      final LanguageKey language,
      final String key,
      final boolean withPrefix,
      final Map<String, String> replacements
  ) {
    Audience checkedAudience = Objects.requireNonNull(audience, "audience");
    LanguageKey checkedLanguage = Objects.requireNonNull(language, "language");
    LocalizedMessage message = localization.resolve(
        checkedLanguage,
        Objects.requireNonNull(key, "key"),
        Objects.requireNonNull(replacements, "replacements")
    );
    Component prefix = withPrefix
        ? localization.resolve(checkedLanguage, prefixKey, Map.of()).getLines().getFirst()
        : Component.empty();
    for (Component line : message.getLines()) {
      checkedAudience.sendMessage(withPrefix ? prefix.append(line) : line);
    }
  }
}
