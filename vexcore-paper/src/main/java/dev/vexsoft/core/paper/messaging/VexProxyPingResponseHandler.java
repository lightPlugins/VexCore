package dev.vexsoft.core.paper.messaging;

import dev.vexsoft.core.api.messaging.MessageContext;
import dev.vexsoft.core.api.messaging.MessageHandler;
import dev.vexsoft.core.api.messaging.MessageType;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.messaging.debug.ProxyDebugMessages;
import dev.vexsoft.core.messaging.debug.ProxyPingResponse;
import java.util.Objects;

@Dependencies({ProxyPingService.class})
public final class VexProxyPingResponseHandler implements MessageHandler<ProxyPingResponse> {

  private final ProxyPingService pings;

  public VexProxyPingResponseHandler(final VexServiceRegistry services) {
    pings = Objects.requireNonNull(services, "services").require(ProxyPingService.class);
  }

  @Override
  public MessageType<ProxyPingResponse> getMessageType() {
    return ProxyDebugMessages.PING_RESPONSE;
  }

  @Override
  public void handle(final ProxyPingResponse message, final MessageContext context) {
    pings.complete(message.getRequestId());
  }
}
