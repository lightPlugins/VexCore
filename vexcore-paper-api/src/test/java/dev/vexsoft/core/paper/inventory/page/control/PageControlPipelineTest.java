package dev.vexsoft.core.paper.inventory.page.control;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vexsoft.core.paper.inventory.InventoryKey;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class PageControlPipelineTest {

  @Test
  void appliesActiveFiltersBeforeSorters() {
    UUID viewerId = UUID.randomUUID();
    InventoryKey key = InventoryKey.of("test:numbers");
    InMemoryPageControlStateStore states = new InMemoryPageControlStateStore();
    BasicPageFilterControl<Integer> filter = BasicPageFilterControl.<Integer>builder("parity")
        .mode("all", Component.text("All"), value -> true)
        .mode("even", Component.text("Even"), value -> value % 2 == 0)
        .defaultMode("all")
        .build();
    BasicPageSortControl<Integer> sort = BasicPageSortControl.<Integer>builder("order")
        .mode("ascending", Component.text("Ascending"), Comparator.naturalOrder())
        .mode("descending", Component.text("Descending"), Comparator.reverseOrder())
        .defaultMode("ascending")
        .build();
    states.setActiveMode(viewerId, key, "main", "parity", "even");
    states.setActiveMode(viewerId, key, "main", "order", "descending");

    List<Integer> result = PageControlPipeline.apply(
        List.of(1, 4, 2, 3),
        viewerId,
        key,
        "main",
        states,
        List.of(filter),
        List.of(sort)
    );

    assertEquals(List.of(4, 2), result);
  }
}
