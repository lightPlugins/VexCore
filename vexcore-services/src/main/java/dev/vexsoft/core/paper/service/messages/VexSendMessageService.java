package dev.vexsoft.core.paper.service.messages;


import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.service.localization.LocalizedMessageService;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.service.players.PaperPlayerService;
import java.util.Map;
import java.util.Objects;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Dependencies({
    LocalizedMessageService.class,
    PaperPlayerService.class
})
public final class VexSendMessageService implements SendMessageService {

  private final LocalizedMessageService messages;
  private final PaperPlayerService players;

  public VexSendMessageService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
    messages = services.require(LocalizedMessageService.class);
    players = services.require(PaperPlayerService.class);
  }

  @Override
  public void send(final CommandSender sender, final String key) {
    send(sender, key, false, Map.of());
  }

  @Override
  public void send(final CommandSender sender, final String key, final boolean withPrefix) {
    send(sender, key, withPrefix, Map.of());
  }

  @Override
  public void send(
      final CommandSender sender,
      final String key,
      final Map<String, String> replacements
  ) {
    send(sender, key, false, replacements);
  }

  @Override
  public void send(
      final CommandSender sender,
      final String key,
      final boolean withPrefix,
      final Map<String, String> replacements
  ) {
    CommandSender checkedSender = Objects.requireNonNull(sender, "sender");
    if (checkedSender instanceof Player player) {
      VexPlayer vexPlayer = players.require(player);
      messages.send(vexPlayer, key, withPrefix, replacements);
      return;
    }
    messages.send(checkedSender, LanguageKey.EN_EN, key, withPrefix, replacements);
  }
}
