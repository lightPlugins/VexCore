package dev.vexsoft.core.inventory.page;

import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.inventory.InventoryContext;
import dev.vexsoft.core.inventory.InventoryKey;
import dev.vexsoft.core.inventory.page.control.InMemoryPageControlStateStore;
import dev.vexsoft.core.inventory.page.control.PageControlInventoryElement;
import dev.vexsoft.core.inventory.page.control.PageControlPipeline;
import dev.vexsoft.core.inventory.page.control.PageControlStateStore;
import dev.vexsoft.core.inventory.page.control.PageFilterControl;
import dev.vexsoft.core.inventory.page.control.PageSortControl;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

public abstract class ControlledPagedInventoryView<T> extends PagedInventoryView<T> {

  private final PageSource<T> source;
  private final String areaId;
  private final PageControlStateStore states;
  private final List<PageFilterControl<T>> filters = new ArrayList<>();
  private final List<PageSortControl<T>> sorts = new ArrayList<>();

  protected ControlledPagedInventoryView(
      final VexServiceRegistry services,
      final InventoryKey key,
      final int size,
      final PageBounds bounds,
      final String areaId,
      final PageSource<T> source,
      final PageItemRenderer<T> itemRenderer
  ) {
    this(
        services,
        key,
        size,
        bounds,
        areaId,
        source,
        itemRenderer,
        new InMemoryPageControlStateStore()
    );
  }

  protected ControlledPagedInventoryView(
      final VexServiceRegistry services,
      final InventoryKey key,
      final int size,
      final PageBounds bounds,
      final String areaId,
      final PageSource<T> source,
      final PageItemRenderer<T> itemRenderer,
      final PageControlStateStore states
  ) {
    super(services, key, size, bounds, source, itemRenderer);
    this.source = Objects.requireNonNull(source, "source");
    this.areaId = Objects.requireNonNull(areaId, "areaId");
    this.states = Objects.requireNonNull(states, "states");
  }

  protected final <C extends PageFilterControl<T>> C addFilterControl(
      final int slot,
      final Component title,
      final Material material,
      final C control
  ) {
    filters.add(Objects.requireNonNull(control, "control"));
    addElement(slot, new PageControlInventoryElement(
        material,
        title,
        getKey(),
        areaId,
        control,
        states,
        this::refreshAndResetPage
    ));
    return control;
  }

  protected final <C extends PageSortControl<T>> C addSortControl(
      final int slot,
      final Component title,
      final Material material,
      final C control
  ) {
    sorts.add(Objects.requireNonNull(control, "control"));
    addElement(slot, new PageControlInventoryElement(
        material,
        title,
        getKey(),
        areaId,
        control,
        states,
        this::refreshAndResetPage
    ));
    return control;
  }

  protected final String getActiveMode(
      final InventoryContext context,
      final dev.vexsoft.core.inventory.page.control.PageControl control
  ) {
    return states.getActiveMode(
        context.getViewer().getUniqueId(),
        getKey(),
        areaId,
        control.getControlId()
    ).orElse(control.getDefaultModeId());
  }

  protected final PageControlStateStore getControlStateStore() {
    return states;
  }

  @Override
  protected List<T> resolveItems(final InventoryContext context) {
    return PageControlPipeline.apply(
        source.getItems(context),
        context.getViewer().getUniqueId(),
        getKey(),
        areaId,
        states,
        filters,
        sorts
    );
  }

  private void refreshAndResetPage(final InventoryContext context) {
    setPage(context, 0);
    context.getInventoryService().refresh(context.getViewer());
  }
}
