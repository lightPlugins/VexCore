package dev.vexsoft.core.velocity.messaging;

import dev.vexsoft.core.api.messaging.MessageContext;
import dev.vexsoft.core.api.messaging.MessageHandler;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.messaging.MessageType;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.messaging.debug.ProxyDebugMessages;
import dev.vexsoft.core.common.messaging.debug.ProxyPingRequest;
import dev.vexsoft.core.common.messaging.debug.ProxyPingResponse;
import java.util.Objects;

@Dependencies({MessagingService.class})
public final class VexProxyPingMessageHandler implements MessageHandler<ProxyPingRequest> {

  private final MessagingService messages;

  public VexProxyPingMessageHandler(final VexServiceRegistry services) {
    messages = Objects.requireNonNull(services, "services").require(MessagingService.class);
  }

  @Override
  public MessageType<ProxyPingRequest> getMessageType() {
    return ProxyDebugMessages.PING_REQUEST;
  }

  @Override
  public void handle(final ProxyPingRequest message, final MessageContext context) {
    if (context.getSourceServer().isBlank()) {
      return;
    }
    messages.send(
        MessageTarget.server(context.getSourceServer()),
        ProxyDebugMessages.PING_RESPONSE,
        new ProxyPingResponse(message.getRequestId())
    );
  }
}
