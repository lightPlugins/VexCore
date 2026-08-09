package dev.vexsoft.core.common.messaging;

import java.util.UUID;
import lombok.Value;

/** Returns a proxy ping identifier to the requesting Paper server */
@Value
public class ProxyPingResponse {
  UUID requestId;
}
