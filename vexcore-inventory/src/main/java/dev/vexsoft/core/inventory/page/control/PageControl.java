package dev.vexsoft.core.inventory.page.control;

import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/**
 * Describes the selectable modes used by a page filter or sorter
 */
public interface PageControl {

  /** Returns the identifier used to store this control state */
  String getControlId();

  /** Returns the modes that can be selected for this control */
  List<String> getModeIds();

  /** Returns the mode selected before a viewer changes this control */
  String getDefaultModeId();

  /** Returns the component displayed for the given mode */
  Component getLabel(String modeId);

  /** Validates the identifiers and modes exposed by this control */
  default void validate() {
    if (Objects.requireNonNull(getControlId(), "controlId").isBlank()) {
      throw new IllegalArgumentException("controlId must not be blank");
    }
    List<String> modes = List.copyOf(Objects.requireNonNull(getModeIds(), "modeIds"));
    if (modes.isEmpty()) {
      throw new IllegalArgumentException("modeIds must not be empty");
    }
    if (!modes.contains(Objects.requireNonNull(getDefaultModeId(), "defaultModeId"))) {
      throw new IllegalArgumentException("defaultModeId must be part of modeIds");
    }
  }
}
