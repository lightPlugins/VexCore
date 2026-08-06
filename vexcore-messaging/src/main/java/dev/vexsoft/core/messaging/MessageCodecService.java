package dev.vexsoft.core.messaging;

import dev.vexsoft.core.api.service.VexService;

/** Encodes and validates the versioned VexCore messaging protocol */
public interface MessageCodecService extends VexService {

  /** Encodes one message into its wire representation */
  byte[] encode(MessageEnvelope message);

  /** Decodes and validates one message from its wire representation */
  MessageEnvelope decode(byte[] data);
}
