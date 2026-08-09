package dev.vexsoft.core.api.service.messaging;

import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.messaging.MessageHandler;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.messaging.MessageType;

import dev.vexsoft.core.api.service.registry.VexService;

/** Sends typed messages and registers owner-scoped network message handlers */
public interface MessagingService extends VexService {

  /** Sends a typed message to the requested network target */
  <T> DeliveryResult send(MessageTarget target, MessageType<T> type, T message);

  /** Creates and registers a message handler through the service registry */
  void register(Class<? extends MessageHandler<?>> handlerType);
}
