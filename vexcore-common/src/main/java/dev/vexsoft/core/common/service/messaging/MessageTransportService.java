package dev.vexsoft.core.common.service.messaging;

import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.function.Consumer;

/** Connects the shared messaging layer to its current server platform */
public interface MessageTransportService extends VexService {

  /** Starts platform listeners after the owning plugin has initialized */
  void start();

  /** Sends one encoded message through the current platform */
  DeliveryResult send(MessageEnvelope message);

  /** Registers a local receiver for decoded incoming messages */
  AutoCloseable subscribe(Consumer<MessageEnvelope> receiver);
}
