package dev.vexsoft.core.paper.message;

import dev.vexsoft.core.api.service.VexService;
import java.util.Map;
import org.bukkit.entity.Player;

/**
 * Sends localized plugin messages to players
 */
public interface SendMessageService extends VexService {

  /** Sends a localized message without a prefix */
  void send(Player player, String key);

  /** Sends a localized message with optional prefix handling */
  void send(Player player, String key, boolean withPrefix);

  /** Sends a localized message with string replacements and no prefix */
  void send(Player player, String key, Map<String, String> replacements);

  /** Sends a localized message with prefix handling and string replacements */
  void send(
      Player player,
      String key,
      boolean withPrefix,
      Map<String, String> replacements
  );
}
