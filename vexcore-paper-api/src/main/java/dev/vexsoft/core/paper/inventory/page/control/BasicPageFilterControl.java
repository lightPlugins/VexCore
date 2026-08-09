package dev.vexsoft.core.paper.inventory.page.control;

import dev.vexsoft.core.paper.inventory.InventoryKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.Getter;
import net.kyori.adventure.text.Component;

/** Immutable multi-mode filter control built from named predicates. */
@Getter
public final class BasicPageFilterControl<T> implements PageFilterControl<T> {

  private final String controlId;
  private final List<String> modeIds;
  private final String defaultModeId;
  private final Map<String, Component> labels;
  private final Map<String, Predicate<T>> predicates;

  private BasicPageFilterControl(final Builder<T> builder) {
    controlId = builder.controlId;
    modeIds = List.copyOf(builder.modeIds);
    defaultModeId = builder.defaultModeId == null ? modeIds.getFirst() : builder.defaultModeId;
    labels = Map.copyOf(builder.labels);
    predicates = Map.copyOf(builder.predicates);
    validate();
  }

  /** Starts a builder for a filter control with the given persistent identifier. */
  public static <T> Builder<T> builder(final String controlId) {
    return new Builder<>(controlId);
  }

  @Override
  public Component getLabel(final String modeId) {
    return labels.getOrDefault(modeId, Component.text(modeId));
  }

  @Override
  public Predicate<T> getPredicate(
      final String modeId,
      final InventoryKey inventoryKey,
      final UUID viewerId
  ) {
    Objects.requireNonNull(inventoryKey, "inventoryKey");
    Objects.requireNonNull(viewerId, "viewerId");
    return Objects.requireNonNull(predicates.getOrDefault(modeId, predicates.get(defaultModeId)));
  }

  /** Builds a filter control from ordered modes and one optional default mode. */
  public static final class Builder<T> {
    private final String controlId;
    private final List<String> modeIds = new ArrayList<>();
    private final Map<String, Component> labels = new LinkedHashMap<>();
    private final Map<String, Predicate<T>> predicates = new LinkedHashMap<>();
    private String defaultModeId;

    private Builder(final String controlId) {
      this.controlId = Objects.requireNonNull(controlId, "controlId");
    }

    /** Adds a selectable mode and the predicate applied while that mode is active. */
    public Builder<T> mode(
        final String modeId,
        final Component label,
        final Predicate<T> predicate
    ) {
      if (modeIds.contains(modeId)) {
        throw new IllegalArgumentException("Duplicate modeId: " + modeId);
      }
      modeIds.add(Objects.requireNonNull(modeId, "modeId"));
      labels.put(modeId, Objects.requireNonNull(label, "label"));
      predicates.put(modeId, Objects.requireNonNull(predicate, "predicate"));
      return this;
    }

    /** Selects the mode used before a viewer has persisted a choice. */
    public Builder<T> defaultMode(final String modeId) {
      defaultModeId = Objects.requireNonNull(modeId, "modeId");
      return this;
    }

    /** Validates the configured modes and creates an immutable filter control. */
    public BasicPageFilterControl<T> build() {
      validateModes(modeIds, defaultModeId);
      return new BasicPageFilterControl<>(this);
    }
  }

  private static void validateModes(final List<String> modes, final String defaultMode) {
    if (modes.isEmpty()) {
      throw new IllegalStateException("At least one mode is required");
    }
    if (defaultMode != null && !modes.contains(defaultMode)) {
      throw new IllegalStateException("defaultModeId must be part of modeIds");
    }
  }
}
