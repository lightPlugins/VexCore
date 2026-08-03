package dev.vexsoft.core.inventory.view;

import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.inventory.InventoryContext;
import dev.vexsoft.core.inventory.InventoryElement;
import dev.vexsoft.core.inventory.InventoryKey;
import dev.vexsoft.core.inventory.InventoryView;
import dev.vexsoft.core.inventory.element.BackInventoryElement;
import dev.vexsoft.core.inventory.element.StaticInventoryElement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

public abstract class AbstractInventoryView implements InventoryView {

  @Getter(AccessLevel.PROTECTED)
  private final VexServiceRegistry services;
  @Getter
  private final InventoryKey key;
  @Getter
  private final int size;
  private final Map<Integer, InventoryElement> elements = new LinkedHashMap<>();
  private Function<InventoryContext, Component> titleProvider = context -> Component.empty();
  private Consumer<InventoryContext> openListener = context -> { };
  private Consumer<InventoryContext> closeListener = context -> { };

  protected AbstractInventoryView(
      final VexServiceRegistry services,
      final InventoryKey key,
      final int size
  ) {
    this.services = Objects.requireNonNull(services, "services");
    this.key = Objects.requireNonNull(key, "key");
    if (size < 9 || size > 54 || size % 9 != 0) {
      throw new IllegalArgumentException("size must be a multiple of 9 between 9 and 54");
    }
    this.size = size;
  }

  @Override
  public final Component getTitle(final InventoryContext context) {
    return Objects.requireNonNull(titleProvider.apply(context), "title");
  }

  @Override
  public Map<Integer, InventoryElement> getElements(final InventoryContext context) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(elements));
  }

  @Override
  public void onOpen(final InventoryContext context) {
    openListener.accept(Objects.requireNonNull(context, "context"));
  }

  @Override
  public void onClose(final InventoryContext context) {
    closeListener.accept(Objects.requireNonNull(context, "context"));
  }

  protected final void setTitle(final Component title) {
    Component checkedTitle = Objects.requireNonNull(title, "title");
    titleProvider = context -> checkedTitle;
  }

  protected final void setTitle(final Function<InventoryContext, Component> titleProvider) {
    this.titleProvider = Objects.requireNonNull(titleProvider, "titleProvider");
  }

  protected final void setOnOpen(final Consumer<InventoryContext> listener) {
    openListener = Objects.requireNonNull(listener, "listener");
  }

  protected final void setOnClose(final Consumer<InventoryContext> listener) {
    closeListener = Objects.requireNonNull(listener, "listener");
  }

  protected final void addElement(final int slot, final InventoryElement element) {
    validateSlot(slot);
    elements.put(slot, Objects.requireNonNull(element, "element"));
  }

  protected final void addElement(final int column, final int row, final InventoryElement element) {
    addElement(toSlot(column, row), element);
  }

  protected final void removeElement(final int slot) {
    validateSlot(slot);
    elements.remove(slot);
  }

  protected final void fill(final InventoryElement element) {
    Objects.requireNonNull(element, "element");
    for (int slot = 0; slot < size; slot++) {
      elements.put(slot, element);
    }
  }

  protected final void fill(final ItemStack item) {
    fill(new StaticInventoryElement(item));
  }

  protected final void setBackButton(final int slot) {
    addElement(slot, new BackInventoryElement());
  }

  protected final void setBackButton(final int slot, final int steps) {
    addElement(slot, new BackInventoryElement(steps));
  }

  protected final void setBackButton(final int slot, final InventoryKey target) {
    addElement(slot, new BackInventoryElement(target));
  }

  protected final void setBackButtonElement(final int slot, final InventoryElement element) {
    addElement(slot, element);
  }

  protected final int toSlot(final int column, final int row) {
    if (column < 0 || column > 8) {
      throw new IllegalArgumentException("column must be between 0 and 8");
    }
    int maxRow = size / 9 - 1;
    if (row < 0 || row > maxRow) {
      throw new IllegalArgumentException("row must be between 0 and " + maxRow);
    }
    return row * 9 + column;
  }

  private void validateSlot(final int slot) {
    if (slot < 0 || slot >= size) {
      throw new IllegalArgumentException("slot must be between 0 and " + (size - 1));
    }
  }
}
