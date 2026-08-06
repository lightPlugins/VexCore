package dev.vexsoft.core.localization;

import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.localization.LocalizedMessageService;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.localization.LocalizationService;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

/** Default platform-neutral localized-message sender. */
@Dependencies(LocalizationService.class)
public final class VexLocalizedMessageService implements LocalizedMessageService {

  private final LocalizationService localization;
  private final String prefixKey;

  public VexLocalizedMessageService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    if (!(checkedServices.getOwner() instanceof LocalizationOwner owner)) {
      throw new IllegalArgumentException("LocalizedMessageService owner must support localizations");
    }
    localization = checkedServices.require(LocalizationService.class);
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
    send(
        checkedPlayer.requirePlatformPlayer(Audience.class),
        language,
        key,
        withPrefix,
        replacements
    );
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
