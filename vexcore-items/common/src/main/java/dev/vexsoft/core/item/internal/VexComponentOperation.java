package dev.vexsoft.core.item.internal;

import lombok.Value;

/**
 * Contains one normalized component mutation
 */
@Value
public class VexComponentOperation {
  VexComponentOperationType type;
  Object value;

  /** Creates an operation that assigns a component value */
  public static VexComponentOperation set(final Object value) {
    return new VexComponentOperation(VexComponentOperationType.SET, value);
  }

  /** Creates an operation that enables a component without a value */
  public static VexComponentOperation setFlag() {
    return new VexComponentOperation(VexComponentOperationType.SET, null);
  }

  /** Creates an operation that removes a component */
  public static VexComponentOperation unset() {
    return new VexComponentOperation(VexComponentOperationType.UNSET, null);
  }

  /** Creates an operation that restores a prototype value */
  public static VexComponentOperation reset() {
    return new VexComponentOperation(VexComponentOperationType.RESET, null);
  }
}
