package dev.vexsoft.core.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.messaging.MessageContext;
import dev.vexsoft.core.api.messaging.MessageHandler;
import dev.vexsoft.core.api.messaging.MessageKey;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.messaging.MessageType;
import dev.vexsoft.core.api.messaging.MessagingService;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexClassFactory;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Dependencies({MessageTransportService.class})
public final class VexMessagingService implements MessagingService, AutoCloseable {

  private static final long DEFAULT_TIME_TO_LIVE_MILLIS = 30_000L;

  private final VexServiceRegistry services;
  private final MessageTransportService transport;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<MessageKey, List<MessageHandler<?>>> handlers = new ConcurrentHashMap<>();
  private final AutoCloseable transportSubscription;

  public VexMessagingService(final VexServiceRegistry services) {
    this.services = Objects.requireNonNull(services, "services");
    this.transport = services.require(MessageTransportService.class);
    this.transportSubscription = transport.subscribe(this::receive);
  }

  @Override
  public <T> DeliveryResult send(
      final MessageTarget target,
      final MessageType<T> type,
      final T message
  ) {
    Objects.requireNonNull(target, "target");
    MessageType<T> checkedType = Objects.requireNonNull(type, "type");
    try {
      byte[] payload = mapper.writeValueAsBytes(Objects.requireNonNull(message, "message"));
      return transport.send(new MessageEnvelope(
          UUID.randomUUID(),
          checkedType.getKey(),
          target,
          services.getOwner().getServiceOwnerName(),
          "",
          System.currentTimeMillis(),
          DEFAULT_TIME_TO_LIVE_MILLIS,
          payload
      ));
    } catch (IOException exception) {
      throw new IllegalArgumentException(
          "Unable to encode message payload for " + checkedType.getKey().asString(),
          exception
      );
    }
  }

  @Override
  public void register(final Class<? extends MessageHandler<?>> handlerType) {
    MessageHandler<?> handler = VexClassFactory.create(
        Objects.requireNonNull(handlerType, "handlerType"),
        services,
        "Message handler"
    );
    handlers.computeIfAbsent(
        handler.getMessageType().getKey(),
        ignored -> new CopyOnWriteArrayList<>()
    ).add(handler);
  }

  @Override
  public void close() throws Exception {
    handlers.clear();
    transportSubscription.close();
  }

  private void receive(final MessageEnvelope envelope) {
    if (envelope.isExpired(System.currentTimeMillis())) {
      return;
    }
    List<MessageHandler<?>> matchingHandlers = handlers.get(envelope.getMessageKey());
    if (matchingHandlers == null) {
      return;
    }
    MessageContext context = new MessageContext(
        envelope.getMessageId(),
        envelope.getSourceOwner(),
        envelope.getSourceServer(),
        envelope.getCreatedAt()
    );
    for (MessageHandler<?> handler : matchingHandlers) {
      dispatch(handler, envelope, context);
    }
  }

  private <T> void dispatch(
      final MessageHandler<T> handler,
      final MessageEnvelope envelope,
      final MessageContext context
  ) {
    MessageType<T> type = handler.getMessageType();
    try {
      T message = mapper.readValue(envelope.getPayload(), type.getPayloadType());
      handler.handle(message, context);
    } catch (IOException exception) {
      throw new IllegalArgumentException(
          "Unable to decode message payload for " + envelope.getMessageKey().asString(),
          exception
      );
    }
  }
}
