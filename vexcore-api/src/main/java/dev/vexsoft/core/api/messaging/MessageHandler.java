package dev.vexsoft.core.api.messaging;

/** Handles one typed message received through VexCore */
public interface MessageHandler<T> {

  /** Returns the message type accepted by this handler */
  MessageType<T> getMessageType();

  /** Processes a decoded message and its delivery context */
  void handle(T message, MessageContext context);
}
