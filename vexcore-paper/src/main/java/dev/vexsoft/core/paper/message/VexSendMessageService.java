package dev.vexsoft.core.paper.message;

import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.localization.LocalizationService;
import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.player.PlayerService;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

@Dependencies({
    LocalizationService.class,
    PlayerService.class
})
public final class VexSendMessageService implements SendMessageService {

  private final LocalizationService localization;
  private final PlayerService players;
  private final String prefixKey;

  public VexSendMessageService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
    if (!(services.getOwner() instanceof LocalizationOwner owner)) {
      throw new IllegalArgumentException("SendMessageService owner must support localizations");
    }
    localization = services.require(LocalizationService.class);
    players = services.require(PlayerService.class);
    prefixKey = owner.getMessagePrefixKey();
  }

  @Override
  public void send(final Player player, final String key) {
    send(player, key, false, Map.of());
  }

  @Override
  public void send(final Player player, final String key, final boolean withPrefix) {
    send(player, key, withPrefix, Map.of());
  }

  @Override
  public void send(
      final Player player,
      final String key,
      final Map<String, String> replacements
  ) {
    send(player, key, false, replacements);
  }

  @Override
  public void send(
      final Player player,
      final String key,
      final boolean withPrefix,
      final Map<String, String> replacements
  ) {
    Objects.requireNonNull(player, "player");
    VexPlayer vexPlayer = players.require(player.getUniqueId());
    LanguageKey language = vexPlayer.getContainer(LanguageContainer.class).getLanguage().getKey();
    LocalizedMessage message = localization.resolve(language, key, replacements);
    Component prefix = withPrefix
        ? localization.resolve(language, prefixKey, Map.of()).getLines().getFirst()
        : Component.empty();
    for (Component line : message.getLines()) {
      player.sendMessage(withPrefix ? prefix.append(line) : line);
    }
  }
}
