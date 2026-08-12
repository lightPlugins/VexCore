package dev.vexsoft.core.execution;

import java.util.Objects;

/** Identifies one replaceable runtime contribution owned by an external system. */
public record ContributionSource(String namespace, String value) {

  /** Validates both lowercase identifier segments. */
  public ContributionSource {
    namespace = validate(namespace, "namespace");
    value = validate(value, "value");
  }

  private static String validate(final String value, final String name) {
    String checked = Objects.requireNonNull(value, name);
    if (!checked.matches("[a-z][a-z0-9_-]*")) {
      throw new IllegalArgumentException(name + " must be a lowercase identifier: " + checked);
    }
    return checked;
  }

  @Override
  public String toString() {
    return namespace + ':' + value;
  }
}
