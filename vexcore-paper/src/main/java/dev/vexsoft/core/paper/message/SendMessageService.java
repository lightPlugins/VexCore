package dev.vexsoft.core.paper.message;

import dev.vexsoft.core.api.service.VexService;
import java.util.Map;
import org.bukkit.entity.Player;

public interface SendMessageService extends VexService {

  /** Sends a localized message without a prefix */
  public void send(Player player, String key);

  /** Sends a localized message with optional prefix handling */
  public void send(Player player, String key, boolean withPrefix);

  /** Sends a localized message with string replacements and no prefix */
  public void send(Player player, String key, Map<String, String> replacements);

  /** Sends a localized message with prefix handling and string replacements */
  public void send(
      Player player,
      String key,
      boolean withPrefix,
      Map<String, String> replacements
  );
}
