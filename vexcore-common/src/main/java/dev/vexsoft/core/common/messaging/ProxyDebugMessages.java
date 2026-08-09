package dev.vexsoft.core.common.messaging;

import dev.vexsoft.core.api.messaging.MessageKey;
import dev.vexsoft.core.api.messaging.MessageType;
import lombok.experimental.UtilityClass;

/** Defines the internal messages used to test communication with Velocity */
@UtilityClass
public class ProxyDebugMessages {

  public static final MessageType<ProxyPingRequest> PING_REQUEST = MessageType.json(
      MessageKey.of("vexcore", "debug-proxy-ping-request"),
      ProxyPingRequest.class
  );

  public static final MessageType<ProxyPingResponse> PING_RESPONSE = MessageType.json(
      MessageKey.of("vexcore", "debug-proxy-ping-response"),
      ProxyPingResponse.class
  );
}
