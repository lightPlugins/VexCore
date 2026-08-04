package dev.vexsoft.core.dialog;

import java.util.Objects;
import java.util.Optional;
import lombok.Getter;

/**
 * Contains the outcome and optional value returned by a dialog
 */
@Getter
public final class DialogResult<T> {

  private final DialogResultType type;
  private final T value;

  private DialogResult(final DialogResultType type, final T value) {
    this.type = Objects.requireNonNull(type, "type");
    this.value = value;
  }

  /** Creates a result containing a submitted value */
  public static <T> DialogResult<T> value(final DialogResultType type, final T value) {
    return new DialogResult<>(type, Objects.requireNonNull(value, "value"));
  }

  /** Creates a result without a submitted value */
  public static <T> DialogResult<T> empty(final DialogResultType type) {
    return new DialogResult<>(type, null);
  }

  /** Returns the submitted value when one is available */
  public Optional<T> getValue() {
    return Optional.ofNullable(value);
  }

  /** Returns whether the player confirmed or submitted the dialog */
  public boolean isConfirmed() {
    return type == DialogResultType.CONFIRMED;
  }
}
