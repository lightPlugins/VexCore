package dev.vexsoft.core.api.messaging;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** Identifies a network message without tying it to a Java class name */
@Getter
@EqualsAndHashCode
@ToString
public final class MessageKey {

  private static final Pattern PART = Pattern.compile("[a-z0-9][a-z0-9._-]*");

  private final String namespace;
  private final String value;

  private MessageKey(final String namespace, final String value) {
    this.namespace = normalize(namespace, "namespace");
    this.value = normalize(value, "value");
  }

  /** Creates a validated message key from its namespace and value */
  public static MessageKey of(final String namespace, final String value) {
    return new MessageKey(namespace, value);
  }

  /** Returns the wire representation used by the messaging protocol */
  public String asString() {
    return namespace + ":" + value;
  }

  private static String normalize(final String value, final String part) {
    String normalized = Objects.requireNonNull(value, part)
        .trim()
        .toLowerCase(Locale.ROOT);
    if (!PART.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Invalid message key " + part + ": " + value);
    }
    return normalized;
  }
}
