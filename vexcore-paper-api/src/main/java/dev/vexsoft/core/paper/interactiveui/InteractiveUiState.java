package dev.vexsoft.core.paper.interactiveui;

import org.jspecify.annotations.Nullable;

/** Immutable snapshot of the most recently processed pointer input. */
public record InteractiveUiState(double x, double y, boolean pressed, @Nullable String hoveredId,
                                 long receivedInputs) {
}
