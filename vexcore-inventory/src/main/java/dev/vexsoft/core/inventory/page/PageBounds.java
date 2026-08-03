package dev.vexsoft.core.inventory.page;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class PageBounds {

  private final List<Integer> slots;

  public static PageBounds ofSlots(final List<Integer> slots) {
    List<Integer> checked = List.copyOf(Objects.requireNonNull(slots, "slots"));
    if (checked.isEmpty()) {
      throw new IllegalArgumentException("slots must not be empty");
    }
    if (checked.stream().anyMatch(slot -> slot < 0 || slot >= 54)) {
      throw new IllegalArgumentException("slots must be between 0 and 53");
    }
    if (new LinkedHashSet<>(checked).size() != checked.size()) {
      throw new IllegalArgumentException("slots must not contain duplicates");
    }
    return new PageBounds(checked);
  }

  public static PageBounds rectangle(
      final int startColumn,
      final int startRow,
      final int width,
      final int height
  ) {
    return rectangle(startColumn, startRow, width, height, false);
  }

  public static PageBounds snakeRectangle(
      final int startColumn,
      final int startRow,
      final int width,
      final int height
  ) {
    return rectangle(startColumn, startRow, width, height, true);
  }

  public int getCapacity() {
    return slots.size();
  }

  public int getSlot(final int localIndex) {
    return slots.get(localIndex);
  }

  private static PageBounds rectangle(
      final int startColumn,
      final int startRow,
      final int width,
      final int height,
      final boolean snake
  ) {
    if (startColumn < 0 || startColumn > 8 || startRow < 0 || startRow > 5) {
      throw new IllegalArgumentException("rectangle start is outside a chest inventory");
    }
    if (width < 1 || height < 1 || startColumn + width > 9 || startRow + height > 6) {
      throw new IllegalArgumentException("rectangle exceeds a chest inventory");
    }
    List<Integer> slots = new ArrayList<>(width * height);
    for (int row = 0; row < height; row++) {
      if (snake && row % 2 != 0) {
        for (int column = width - 1; column >= 0; column--) {
          slots.add((startRow + row) * 9 + startColumn + column);
        }
      } else {
        for (int column = 0; column < width; column++) {
          slots.add((startRow + row) * 9 + startColumn + column);
        }
      }
    }
    return new PageBounds(List.copyOf(slots));
  }
}
