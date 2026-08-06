package dev.vexsoft.core.messaging.debug;

import java.util.UUID;
import lombok.Value;

/** Carries the identifier Velocity must return for a proxy ping */
@Value
public class ProxyPingRequest {
  UUID requestId;
}
