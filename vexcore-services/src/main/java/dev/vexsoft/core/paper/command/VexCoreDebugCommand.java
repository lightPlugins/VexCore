package dev.vexsoft.core.paper.command;

import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.messaging.debug.ProxyDebugMessages;
import dev.vexsoft.core.common.messaging.debug.ProxyPingRequest;
import dev.vexsoft.core.paper.service.messages.SendMessageService;
import dev.vexsoft.core.paper.service.messaging.ProxyPingService;
import dev.vexsoft.core.paper.service.performance.PerformanceBossBarService;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Debug and diagnostics commands below the shared VexCore root. */
@CommandRoot(name = "vexcore", description = "Manages VexCore")
@Dependencies({
    SendMessageService.class,
    MessagingService.class,
    ProxyPingService.class,
    PerformanceBossBarService.class
})
public final class VexCoreDebugCommand {

  private final SendMessageService messages;
  private final MessagingService messaging;
  private final ProxyPingService proxyPings;
  private final PerformanceBossBarService performanceBossBars;

  public VexCoreDebugCommand(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    messages = checkedServices.require(SendMessageService.class);
    messaging = checkedServices.require(MessagingService.class);
    proxyPings = checkedServices.require(ProxyPingService.class);
    performanceBossBars = checkedServices.require(PerformanceBossBarService.class);
  }

  /** Sends a diagnostic round trip to the connected proxy. */
  @Command(
      value = "debug proxy ping",
      permission = "vexcore.command.debug.proxy.ping",
      playerOnly = true
  )
  public int proxyPing(final VexCommandSource source) {
    Player player = (Player) source.getSender();
    UUID requestId = proxyPings.begin(player);
    DeliveryResult result = messaging.send(
        MessageTarget.proxy(),
        ProxyDebugMessages.PING_REQUEST,
        new ProxyPingRequest(requestId)
    );
    if (result == DeliveryResult.SENT || result == DeliveryResult.QUEUED) {
      messages.send(player, "commands.vexcore.debug.proxy.ping.started", true);
      return 1;
    }
    proxyPings.cancel(requestId);
    messages.send(
        player,
        "commands.vexcore.debug.proxy.ping.failed",
        true,
        Map.of("reason", result.name().toLowerCase(Locale.ROOT))
    );
    return 0;
  }

  /** Toggles the live performance display for the executing player. */
  @Command(
      value = "debug performance toggle",
      permission = "vexcore.command.debug.performance",
      playerOnly = true
  )
  public int togglePerformance(final VexCommandSource source) {
    Player player = (Player) source.getSender();
    boolean visible = performanceBossBars.toggle(player);
    messages.send(
        player,
        visible
            ? "commands.vexcore.debug.performance.enabled"
            : "commands.vexcore.debug.performance.disabled",
        true
    );
    return 1;
  }
}
