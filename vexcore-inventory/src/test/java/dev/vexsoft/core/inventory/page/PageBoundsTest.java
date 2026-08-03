package dev.vexsoft.core.inventory.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageBoundsTest {

  @Test
  void createsSnakeRowsInAlternatingDirections() {
    PageBounds bounds = PageBounds.snakeRectangle(1, 1, 3, 2);

    assertEquals(List.of(10, 11, 12, 21, 20, 19), bounds.getSlots());
  }

  @Test
  void rejectsDuplicateSlots() {
    assertThrows(IllegalArgumentException.class, () -> PageBounds.ofSlots(List.of(1, 1)));
  }
}
