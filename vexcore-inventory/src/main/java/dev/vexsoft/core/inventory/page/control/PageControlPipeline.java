package dev.vexsoft.core.inventory.page.control;

import dev.vexsoft.core.inventory.InventoryKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PageControlPipeline {

  public static <T> List<T> apply(
      final List<T> items,
      final UUID viewerId,
      final InventoryKey inventoryKey,
      final String areaId,
      final PageControlStateStore states,
      final List<? extends PageFilterControl<T>> filters,
      final List<? extends PageSortControl<T>> sorts
  ) {
    List<T> result = new ArrayList<>(items);
    for (PageFilterControl<T> filter : filters) {
      String mode = states.getActiveMode(
          viewerId,
          inventoryKey,
          areaId,
          filter.getControlId()
      ).orElse(filter.getDefaultModeId());
      result = result.stream()
          .filter(filter.getPredicate(mode, inventoryKey, viewerId))
          .toList();
    }

    Comparator<T> combined = null;
    for (PageSortControl<T> sort : sorts) {
      String mode = states.getActiveMode(
          viewerId,
          inventoryKey,
          areaId,
          sort.getControlId()
      ).orElse(sort.getDefaultModeId());
      Comparator<T> comparator = sort.getComparator(mode, inventoryKey, viewerId);
      combined = combined == null ? comparator : combined.thenComparing(comparator);
    }
    if (combined != null) {
      result = result.stream().sorted(combined).toList();
    }
    return result;
  }
}
