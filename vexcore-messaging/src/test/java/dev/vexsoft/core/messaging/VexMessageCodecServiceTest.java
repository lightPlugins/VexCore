package dev.vexsoft.core.messaging;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.messaging.MessageKey;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.service.DefaultServiceRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VexMessageCodecServiceTest {

  private VexMessageCodecService codec;

  @BeforeEach
  void setUp() {
    ServiceOwner owner = () -> "MessagingTest";
    codec = new VexMessageCodecService(new DefaultServiceRegistry().scoped(owner));
  }

  @Test
  void roundTripsVersionedMessage() {
    UUID messageId = UUID.randomUUID();
    MessageEnvelope original = new MessageEnvelope(
        messageId,
        MessageKey.of("vexskills", "experience-changed"),
        MessageTarget.server("survival-2"),
        "VexSkills",
        "survival-1",
        100L,
        30_000L,
        new byte[] {1, 2, 3}
    );

    MessageEnvelope decoded = codec.decode(codec.encode(original));

    assertEquals(messageId, decoded.getMessageId());
    assertEquals(original.getMessageKey(), decoded.getMessageKey());
    assertEquals(original.getTarget(), decoded.getTarget());
    assertEquals("VexSkills", decoded.getSourceOwner());
    assertEquals("survival-1", decoded.getSourceServer());
    assertArrayEquals(original.getPayload(), decoded.getPayload());
  }

  @Test
  void rejectsUnknownMessageHeader() {
    assertThrows(
        IllegalArgumentException.class,
        () -> codec.decode(new byte[] {0, 0, 0, 0})
    );
  }

  @Test
  void detectsExpiredMessages() {
    MessageEnvelope message = new MessageEnvelope(
        UUID.randomUUID(),
        MessageKey.of("vexcore", "test"),
        MessageTarget.proxy(),
        "VexCore",
        "",
        100L,
        50L,
        new byte[0]
    );

    assertTrue(message.isExpired(150L));
  }
}
