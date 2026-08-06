package dev.vexsoft.core.api.messaging;

import dev.vexsoft.core.api.service.VexService;

/** Sends typed messages and registers owner-scoped network message handlers */
public interface MessagingService extends VexService {

  /** Sends a typed message to the requested network target */
  <T> DeliveryResult send(MessageTarget target, MessageType<T> type, T message);

  /** Creates and registers a message handler through the service registry */
  void register(Class<? extends MessageHandler<?>> handlerType);
}
