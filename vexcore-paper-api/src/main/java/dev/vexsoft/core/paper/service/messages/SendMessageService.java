package dev.vexsoft.core.paper.service.messages;


import dev.vexsoft.core.api.service.registry.VexService;
import java.util.Map;
import org.bukkit.command.CommandSender;

/**
 * Adapts native Paper command senders to the platform-neutral localized-message service.
 */
public interface SendMessageService extends VexService {

  /** Sends a localized message without a prefix. */
  void send(CommandSender sender, String key);

  /** Sends a localized message with optional prefix handling. */
  void send(CommandSender sender, String key, boolean withPrefix);

  /** Sends a localized message with string replacements and no prefix. */
  void send(CommandSender sender, String key, Map<String, String> replacements);

  /** Sends a localized message with prefix handling and string replacements. */
  void send(
      CommandSender sender,
      String key,
      boolean withPrefix,
      Map<String, String> replacements
  );
}
