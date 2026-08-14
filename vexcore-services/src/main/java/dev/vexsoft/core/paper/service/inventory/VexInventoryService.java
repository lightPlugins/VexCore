package dev.vexsoft.core.paper.service.inventory;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexClassFactory;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.inventory.InventoryContext;
import dev.vexsoft.core.paper.inventory.InventoryDefinition;
import dev.vexsoft.core.paper.inventory.InventoryElement;
import dev.vexsoft.core.paper.inventory.InventoryKey;
import dev.vexsoft.core.paper.inventory.InventoryView;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

@Dependencies({ScheduleService.class})
public final class VexInventoryService implements InventoryService, AutoCloseable {

  private final VexServiceRegistry services;
  private final ScheduleService schedules;
  private final VexInventoryRenderer renderer = new VexInventoryRenderer();
  private final Map<InventoryKey, InventoryDefinition> definitions = new LinkedHashMap<>();
  private final Map<UUID, VexInventorySession> sessions = new ConcurrentHashMap<>();
  private boolean closed;

  public VexInventoryService(final VexServiceRegistry services) {
    this.services = Objects.requireNonNull(services, "services");
    this.schedules = services.require(ScheduleService.class);
    if (!(services.getOwner() instanceof Plugin)) {
      throw new IllegalArgumentException("InventoryService owner must be a Bukkit plugin");
    }
  }

  @Override
  public synchronized void register(final Class<? extends InventoryDefinition> definitionType) {
    ensureOpen();
    InventoryDefinition definition = VexClassFactory.create(
        definitionType,
        services,
        "Inventory definition"
    );
    InventoryKey key = Objects.requireNonNull(definition.getKey(), "inventory key");
    if (definitions.putIfAbsent(key, definition) != null) {
      throw new IllegalStateException("Inventory definition is already registered: " + key);
    }
  }

  @Override
  public synchronized Collection<InventoryKey> getKeys() {
    return List.copyOf(definitions.keySet());
  }

  @Override
  public void open(final Player player, final InventoryKey key) {
    execute(player, () -> openNow(player, createView(player, key), true, false));
  }

  @Override
  public void open(final Player player, final InventoryView view) {
    execute(player, () -> openNow(player, view, true, false));
  }

  @Override
  public void replace(final Player player, final InventoryKey key) {
    execute(player, () -> openNow(player, createView(player, key), false, false));
  }

  @Override
  public void replace(final Player player, final InventoryView view) {
    execute(player, () -> openNow(player, view, false, false));
  }

  @Override
  public void refresh(final Player player) {
    execute(player, () -> refreshNow(player));
  }

  @Override
  public void back(final Player player) {
    execute(player, () -> {
      VexInventorySession session = sessions.get(player.getUniqueId());
      if (session == null || session.getHistory().isEmpty()) {
        closeNow(player);
        return;
      }
      openNow(player, session.pop(1), false, false);
    });
  }

  @Override
  public void back(final Player player, final int steps) {
    if (steps < 1) {
      throw new IllegalArgumentException("steps must be at least one");
    }
    execute(player, () -> {
      VexInventorySession session = requireSession(player);
      openNow(player, session.pop(steps), false, false);
    });
  }

  @Override
  public void backTo(final Player player, final InventoryKey key) {
    Objects.requireNonNull(key, "key");
    execute(player, () -> {
      VexInventorySession session = requireSession(player);
      InventoryView target = session.popTo(key).orElseThrow(() -> new IllegalStateException(
          "Inventory is not present in the viewer history: " + key
      ));
      openNow(player, target, false, false);
    });
  }

  @Override
  public void openRoot(final Player player, final InventoryKey key) {
    execute(player, () -> openNow(player, createView(player, key), false, true));
  }

  @Override
  public void openRoot(final Player player, final InventoryView view) {
    execute(player, () -> openNow(player, view, false, true));
  }

  @Override
  public void close(final Player player) {
    execute(player, () -> closeNow(player));
  }

  @Override
  public Optional<InventoryKey> getCurrentInventory(final UUID viewerId) {
    return getCurrentView(viewerId).map(InventoryView::getKey);
  }

  @Override
  public Optional<InventoryView> getCurrentView(final UUID viewerId) {
    VexInventorySession session = sessions.get(Objects.requireNonNull(viewerId, "viewerId"));
    return session == null ? Optional.empty() : Optional.ofNullable(session.getCurrentView());
  }

  @Override
  public synchronized void close() {
    if (!closed) {
      closed = true;
      sessions.clear();
      definitions.clear();
    }
  }

  VexInventorySession getSession(final UUID viewerId) {
    return sessions.get(viewerId);
  }

  InventoryContext createContext(final Player player) {
    return new InventoryContext(services, player, this);
  }

  void handleClose(final Player player, final VexInventoryHolder holder) {
    VexInventorySession session = sessions.get(holder.getViewerId());
    if (session == null) {
      return;
    }
    if (session.isSuppressNextClose()) {
      session.setSuppressNextClose(false);
      return;
    }
    sessions.remove(holder.getViewerId(), session);
    InventoryView current = session.getCurrentView();
    if (current != null) {
      current.onClose(createContext(player));
    }
  }

  void handleQuit(final Player player) {
    VexInventorySession session = sessions.remove(player.getUniqueId());
    if (session != null && session.getCurrentView() != null) {
      session.getCurrentView().onClose(createContext(player));
    }
  }

  private void execute(final Player player, final Runnable action) {
    ensureOpen();
    Player checkedPlayer = Objects.requireNonNull(player, "player");
    if (Bukkit.isOwnedByCurrentRegion(checkedPlayer)) {
      action.run();
      return;
    }
    schedules.runFor(
        checkedPlayer,
        action,
        () -> sessions.remove(checkedPlayer.getUniqueId())
    );
  }

  private InventoryView createView(final Player player, final InventoryKey key) {
    InventoryDefinition definition;
    synchronized (this) {
      definition = definitions.get(Objects.requireNonNull(key, "key"));
    }
    if (definition == null) {
      throw new IllegalArgumentException("Unknown inventory key: " + key);
    }
    return Objects.requireNonNull(
        definition.create(createContext(player)),
        "inventory view"
    );
  }

  private void openNow(
      final Player player,
      final InventoryView view,
      final boolean pushHistory,
      final boolean clearHistory
  ) {
    Objects.requireNonNull(view, "view");
    UUID viewerId = player.getUniqueId();
    VexInventorySession session = sessions.computeIfAbsent(viewerId, ignored -> {
      VexInventoryHolder holder = new VexInventoryHolder(viewerId, view.getKey());
      return new VexInventorySession(viewerId, holder);
    });
    if (clearHistory) {
      session.getHistory().clear();
    }

    InventoryContext context = createContext(player);
    InventoryView current = session.getCurrentView();
    boolean switching = current != null && current != view;
    if (switching && pushHistory) {
      session.push(current);
    }
    if (switching) {
      current.onClose(context);
    }

    InventoryHolder openHolder = player.getOpenInventory().getTopInventory().getHolder();
    session.setSuppressNextClose(openHolder == session.getHolder());
    Map<Integer, InventoryElement> elements = snapshotElements(view, context);
    Inventory inventory = renderer.render(context, view, session.getHolder(), elements);
    session.setInventory(inventory);
    session.updateRenderedElements(elements);
    session.setCurrentView(view);
    player.openInventory(inventory);
    view.onOpen(context);
  }

  private void refreshNow(final Player player) {
    VexInventorySession session = sessions.get(player.getUniqueId());
    if (session == null || session.getCurrentView() == null) {
      return;
    }
    InventoryView view = session.getCurrentView();
    InventoryContext context = createContext(player);
    Map<Integer, InventoryElement> elements = snapshotElements(view, context);
    Inventory openInventory = player.getOpenInventory().getTopInventory();
    boolean sameInventory = openInventory == session.getInventory()
        && openInventory.getHolder() == session.getHolder();
    boolean sameShape = sameInventory && openInventory.getSize() == view.getSize();
    boolean sameTitle = sameShape && Objects.equals(
        player.getOpenInventory().title(),
        view.getTitle(context)
    );
    if (sameTitle) {
      renderer.renderInto(context, openInventory, elements);
      session.updateRenderedElements(elements);
      return;
    }

    session.setSuppressNextClose(sameInventory);
    Inventory inventory = renderer.render(context, view, session.getHolder(), elements);
    session.setInventory(inventory);
    session.updateRenderedElements(elements);
    player.openInventory(inventory);
  }

  private Map<Integer, InventoryElement> snapshotElements(
      final InventoryView view,
      final InventoryContext context
  ) {
    return Map.copyOf(Objects.requireNonNull(view.getElements(context), "inventory elements"));
  }

  private void closeNow(final Player player) {
    VexInventorySession session = sessions.remove(player.getUniqueId());
    if (session != null && session.getCurrentView() != null) {
      session.getCurrentView().onClose(createContext(player));
    }
    player.closeInventory();
  }

  private VexInventorySession requireSession(final Player player) {
    VexInventorySession session = sessions.get(player.getUniqueId());
    if (session == null || session.getCurrentView() == null) {
      throw new IllegalStateException("Player has no active inventory session");
    }
    return session;
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("InventoryService is already closed");
    }
  }

}
