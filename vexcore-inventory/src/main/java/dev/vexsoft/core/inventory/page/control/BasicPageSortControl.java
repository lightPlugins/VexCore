package dev.vexsoft.core.inventory.page.control;

import dev.vexsoft.core.inventory.InventoryKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import net.kyori.adventure.text.Component;

@Getter
public final class BasicPageSortControl<T> implements PageSortControl<T> {

  private final String controlId;
  private final List<String> modeIds;
  private final String defaultModeId;
  private final Map<String, Component> labels;
  private final Map<String, Comparator<T>> comparators;

  private BasicPageSortControl(final Builder<T> builder) {
    controlId = builder.controlId;
    modeIds = List.copyOf(builder.modeIds);
    defaultModeId = builder.defaultModeId == null ? modeIds.getFirst() : builder.defaultModeId;
    labels = Map.copyOf(builder.labels);
    comparators = Map.copyOf(builder.comparators);
    validate();
  }

  public static <T> Builder<T> builder(final String controlId) {
    return new Builder<>(controlId);
  }

  @Override
  public Component getLabel(final String modeId) {
    return labels.getOrDefault(modeId, Component.text(modeId));
  }

  @Override
  public Comparator<T> getComparator(
      final String modeId,
      final InventoryKey inventoryKey,
      final UUID viewerId
  ) {
    Objects.requireNonNull(inventoryKey, "inventoryKey");
    Objects.requireNonNull(viewerId, "viewerId");
    return Objects.requireNonNull(comparators.getOrDefault(modeId, comparators.get(defaultModeId)));
  }

  public static final class Builder<T> {
    private final String controlId;
    private final List<String> modeIds = new ArrayList<>();
    private final Map<String, Component> labels = new LinkedHashMap<>();
    private final Map<String, Comparator<T>> comparators = new LinkedHashMap<>();
    private String defaultModeId;

    private Builder(final String controlId) {
      this.controlId = Objects.requireNonNull(controlId, "controlId");
    }

    public Builder<T> mode(
        final String modeId,
        final Component label,
        final Comparator<T> comparator
    ) {
      if (modeIds.contains(modeId)) {
        throw new IllegalArgumentException("Duplicate modeId: " + modeId);
      }
      modeIds.add(Objects.requireNonNull(modeId, "modeId"));
      labels.put(modeId, Objects.requireNonNull(label, "label"));
      comparators.put(modeId, Objects.requireNonNull(comparator, "comparator"));
      return this;
    }

    public Builder<T> defaultMode(final String modeId) {
      defaultModeId = Objects.requireNonNull(modeId, "modeId");
      return this;
    }

    public BasicPageSortControl<T> build() {
      if (modeIds.isEmpty()) {
        throw new IllegalStateException("At least one mode is required");
      }
      if (defaultModeId != null && !modeIds.contains(defaultModeId)) {
        throw new IllegalStateException("defaultModeId must be part of modeIds");
      }
      return new BasicPageSortControl<>(this);
    }
  }
}
