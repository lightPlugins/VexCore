package dev.vexsoft.core.paper.inventory.page;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.inventory.InventoryContext;
import dev.vexsoft.core.paper.inventory.InventoryElement;
import dev.vexsoft.core.paper.inventory.InventoryKey;
import dev.vexsoft.core.paper.inventory.element.NextPageInventoryElement;
import dev.vexsoft.core.paper.inventory.element.PreviousPageInventoryElement;
import dev.vexsoft.core.paper.inventory.element.RefreshableInventoryElement;
import dev.vexsoft.core.paper.inventory.element.StaticInventoryElement;
import dev.vexsoft.core.paper.inventory.view.AbstractInventoryView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Base inventory view that maps a dynamic item source into bounded, navigable pages. */
public abstract class PagedInventoryView<T> extends AbstractInventoryView {

  private final PageBounds bounds;
  private final PageSource<T> source;
  private final PageItemRenderer<T> itemRenderer;
  private final Map<UUID, Long> lastRefreshByViewer = new ConcurrentHashMap<>();
  private int page;
  private Integer previousSlot;
  private Integer nextSlot;
  private Integer refreshSlot;
  private Integer indicatorSlot;
  private InventoryElement previousElement;
  private InventoryElement nextElement;
  private InventoryElement refreshElement;
  private InventoryElement indicatorElement;
  private long refreshCooldownMillis = 1000L;

  protected PagedInventoryView(
      final VexServiceRegistry services,
      final InventoryKey key,
      final int size,
      final PageBounds bounds,
      final PageSource<T> source,
      final PageItemRenderer<T> itemRenderer
  ) {
    super(services, key, size);
    this.bounds = Objects.requireNonNull(bounds, "bounds");
    this.source = Objects.requireNonNull(source, "source");
    this.itemRenderer = Objects.requireNonNull(itemRenderer, "itemRenderer");
    if (bounds.getSlots().stream().anyMatch(slot -> slot >= size)) {
      throw new IllegalArgumentException("page bounds contain a slot outside the inventory");
    }
  }

  /** Returns the current zero-based page index. */
  public final int getPage() {
    return page;
  }

  /** Returns the number of pages currently required for the viewer's resolved items. */
  public final int getPageCount(final InventoryContext context) {
    return getMaximumPage(resolveItems(context)) + 1;
  }

  /** Advances one page when another page exists. */
  public final boolean nextPage(final InventoryContext context) {
    int nextPage = Math.min(page + 1, getMaximumPage(resolveItems(context)));
    if (nextPage == page) {
      return false;
    }
    page = nextPage;
    return true;
  }

  /** Moves back one page when the current page is not the first page. */
  public final boolean previousPage() {
    if (page == 0) {
      return false;
    }
    page--;
    return true;
  }

  /** Selects a page after clamping it to the currently available range. */
  public final void setPage(final InventoryContext context, final int page) {
    this.page = Math.max(0, Math.min(page, getMaximumPage(resolveItems(context))));
  }

  @Override
  public Map<Integer, InventoryElement> getElements(final InventoryContext context) {
    Map<Integer, InventoryElement> elements = new LinkedHashMap<>(super.getElements(context));
    List<T> items = resolveItems(context);
    page = Math.min(page, getMaximumPage(items));

    for (int localIndex = 0; localIndex < bounds.getCapacity(); localIndex++) {
      int absoluteIndex = page * bounds.getCapacity() + localIndex;
      if (absoluteIndex < items.size()) {
        InventoryElement element = Objects.requireNonNull(
            itemRenderer.render(context, items.get(absoluteIndex), absoluteIndex),
            "page element"
        );
        elements.put(bounds.getSlot(localIndex), element);
      }
    }

    if (previousSlot != null) {
      elements.put(
          previousSlot,
          previousElement == null
              ? new PreviousPageInventoryElement(this::previousPage)
              : previousElement
      );
    }
    if (nextSlot != null) {
      elements.put(
          nextSlot,
          nextElement == null
              ? new NextPageInventoryElement(this::nextPage)
              : nextElement
      );
    }
    if (refreshSlot != null) {
      elements.put(
          refreshSlot,
          refreshElement == null ? createRefreshElement() : refreshElement
      );
    }
    if (indicatorSlot != null) {
      elements.put(
          indicatorSlot,
          indicatorElement == null ? createIndicatorElement(items) : indicatorElement
      );
    }
    return elements;
  }

  protected List<T> resolveItems(final InventoryContext context) {
    return List.copyOf(Objects.requireNonNull(source.getItems(context), "page items"));
  }

  protected final void setPreviousButton(final int slot) {
    previousSlot = slot;
    previousElement = null;
  }

  protected final void setPreviousButton(final int slot, final ItemStack item) {
    previousSlot = slot;
    previousElement = new PreviousPageInventoryElement(item, this::previousPage);
  }

  protected final void setPreviousButton(
      final int slot,
      final Function<InventoryContext, ItemStack> itemProvider
  ) {
    previousSlot = slot;
    previousElement = new PreviousPageInventoryElement(itemProvider, this::previousPage);
  }

  protected final void setPreviousButtonElement(final int slot, final InventoryElement element) {
    previousSlot = slot;
    previousElement = Objects.requireNonNull(element, "element");
  }

  protected final void setNextButton(final int slot) {
    nextSlot = slot;
    nextElement = null;
  }

  protected final void setNextButton(final int slot, final ItemStack item) {
    nextSlot = slot;
    nextElement = new NextPageInventoryElement(item, this::nextPage);
  }

  protected final void setNextButton(
      final int slot,
      final Function<InventoryContext, ItemStack> itemProvider
  ) {
    nextSlot = slot;
    nextElement = new NextPageInventoryElement(itemProvider, this::nextPage);
  }

  protected final void setNextButtonElement(final int slot, final InventoryElement element) {
    nextSlot = slot;
    nextElement = Objects.requireNonNull(element, "element");
  }

  protected final void setRefreshButton(final int slot) {
    refreshSlot = slot;
    refreshElement = null;
  }

  protected final void setRefreshButton(final int slot, final ItemStack item) {
    setRefreshButton(slot, context -> item);
  }

  protected final void setRefreshButton(
      final int slot,
      final Function<InventoryContext, ItemStack> itemProvider
  ) {
    refreshSlot = slot;
    refreshElement = createRefreshElement(itemProvider);
  }

  protected final void setRefreshButtonElement(final int slot, final InventoryElement element) {
    refreshSlot = slot;
    refreshElement = Objects.requireNonNull(element, "element");
  }

  protected final void setRefreshCooldownMillis(final long refreshCooldownMillis) {
    if (refreshCooldownMillis < 0L) {
      throw new IllegalArgumentException("refreshCooldownMillis must not be negative");
    }
    this.refreshCooldownMillis = refreshCooldownMillis;
  }

  protected final void setPageIndicator(final int slot) {
    indicatorSlot = slot;
    indicatorElement = null;
  }

  protected final void setPageIndicator(final int slot, final ItemStack item) {
    setPageIndicator(slot, context -> item);
  }

  protected final void setPageIndicator(
      final int slot,
      final Function<InventoryContext, ItemStack> itemProvider
  ) {
    indicatorSlot = slot;
    indicatorElement = new RefreshableInventoryElement(
        itemProvider,
        (context, event) -> context.getInventoryService().refresh(context.getViewer())
    );
  }

  protected final void setPageIndicatorElement(final int slot, final InventoryElement element) {
    indicatorSlot = slot;
    indicatorElement = Objects.requireNonNull(element, "element");
  }

  private int getMaximumPage(final List<T> items) {
    if (items.isEmpty()) {
      return 0;
    }
    return (items.size() - 1) / bounds.getCapacity();
  }

  private InventoryElement createRefreshElement() {
    ItemStack item = namedItem(Material.CLOCK, Component.text("Refresh Page"));
    return createRefreshElement(context -> item);
  }

  private InventoryElement createRefreshElement(
      final Function<InventoryContext, ItemStack> itemProvider
  ) {
    return new RefreshableInventoryElement(itemProvider, (context, event) -> {
      UUID viewerId = context.getViewer().getUniqueId();
      long now = System.currentTimeMillis();
      long previous = lastRefreshByViewer.getOrDefault(viewerId, 0L);
      if (now - previous >= refreshCooldownMillis) {
        lastRefreshByViewer.put(viewerId, now);
        context.getInventoryService().refresh(context.getViewer());
      }
    });
  }

  private InventoryElement createIndicatorElement(final List<T> items) {
    ItemStack item = namedItem(
        Material.PAPER,
        Component.text("Page " + (page + 1) + "/" + (getMaximumPage(items) + 1))
    );
    return new StaticInventoryElement(
        item,
        (context, event) -> context.getInventoryService().refresh(context.getViewer())
    );
  }

  private ItemStack namedItem(final Material material, final Component name) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(name);
    item.setItemMeta(meta);
    return item;
  }
}
