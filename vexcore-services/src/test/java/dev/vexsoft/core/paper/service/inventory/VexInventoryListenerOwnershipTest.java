package dev.vexsoft.core.paper.service.inventory;

import static org.junit.jupiter.api.Assertions.*;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.inventory.InventoryContext;
import dev.vexsoft.core.paper.inventory.InventoryElement;
import dev.vexsoft.core.paper.inventory.InventoryKey;
import dev.vexsoft.core.paper.inventory.MutableInventoryView;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/** Reproduces multiple plugin listeners seeing the same mutable inventory event. */
public final class VexInventoryListenerOwnershipTest {
  @Test void foreignListenersLeaveLowerInventoryClicksUncancelledInEitherOrder() throws Exception {
    Fixture f = new Fixture();
    for (boolean foreignFirst : new boolean[]{true, false}) {
      InventoryClickEvent click = f.click(60);
      if (foreignFirst) f.foreignListener.onClick(click);
      f.ownerListener.onClick(click);
      if (!foreignFirst) f.foreignListener.onClick(click);
      assertFalse(click.isCancelled());
    }
    assertEquals(2, f.menu.lowerClicks);
  }

  @Test void foreignListenersCannotSwallowTheNextPageButton() throws Exception {
    Fixture f = new Fixture();
    InventoryClickEvent click = f.click(44);
    f.foreignListener.onClick(click);
    f.ownerListener.onClick(click);
    assertTrue(click.isCancelled(), "Menu button must not be taken out");
    assertEquals(1, f.menu.page);
  }

  @Test void existingProtectionCancellationsRemainEffective() throws Exception {
    Fixture f = new Fixture();
    InventoryClickEvent click = f.click(44);
    click.setCancelled(true);
    f.foreignListener.onClick(click);
    f.ownerListener.onClick(click);
    assertTrue(click.isCancelled());
    assertEquals(0, f.menu.page);
  }

  @Test void foreignCloseEventsCannotRetireAnOwnersCurrentMenu() throws Exception {
    Fixture f = new Fixture();
    VexInventoryHolder foreignHolder = new VexInventoryHolder(f.foreign, f.viewerId, InventoryKey.of("test:other"));
    f.owner.handleClose(f.player, foreignHolder);
    assertSame(f.menu, f.owner.getSession(f.viewerId).getCurrentView());
  }

  @Test void foreignDragEventsRemainMovable() throws Exception {
    Fixture f = new Fixture();
    InventoryDragEvent drag = new InventoryDragEvent(f.view, null, null, false, Map.of());
    f.foreignListener.onDrag(drag);
    f.ownerListener.onDrag(drag);
    assertFalse(drag.isCancelled());
    assertEquals(1, f.menu.drags);
  }

  private static final class Fixture {
    final UUID viewerId = UUID.randomUUID();
    final VexInventoryService owner;
    final VexInventoryService foreign;
    final VexInventoryListener ownerListener;
    final VexInventoryListener foreignListener;
    final TestMenu menu = new TestMenu();
    final Player player;
    final InventoryView view;

    @SuppressWarnings("unchecked")
    Fixture() throws Exception {
      VexServiceRegistry ownerRegistry = registry();
      VexServiceRegistry foreignRegistry = registry();
      owner = ownerRegistry.require(InventoryService.class) instanceof VexInventoryService service ? service : null;
      foreign = (VexInventoryService) foreignRegistry.require(InventoryService.class);
      ownerListener = new VexInventoryListener(ownerRegistry);
      foreignListener = new VexInventoryListener(foreignRegistry);
      player = proxy(Player.class, (p,m,a) -> m.getName().equals("getUniqueId") ? viewerId : null);
      VexInventoryHolder holder = new VexInventoryHolder(owner, viewerId, menu.getKey());
      Inventory top = proxy(Inventory.class, (p,m,a) -> switch(m.getName()) {
        case "getHolder" -> holder;
        case "getSize" -> 54;
        default -> null;
      });
      view = proxy(InventoryView.class, (p,m,a) -> switch(m.getName()) {
        case "getTopInventory" -> top;
        case "getPlayer" -> player;
        case "convertSlot" -> (int)a[0];
        default -> null;
      });
      VexInventorySession session = new VexInventorySession(viewerId, holder);
      session.setCurrentView(menu);
      var field = VexInventoryService.class.getDeclaredField("sessions");
      field.setAccessible(true);
      ((Map<UUID,VexInventorySession>)field.get(owner)).put(viewerId, session);
    }
    InventoryClickEvent click(int slot) {
      return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }
  }

  private static VexServiceRegistry registry() {
    Object plugin = Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class,ServiceOwner.class}, (p,m,a) -> null);
    ScheduleService schedules = proxy(ScheduleService.class, (p,m,a) -> null);
    VexInventoryService[] inventory = new VexInventoryService[1];
    VexServiceRegistry registry = proxy(VexServiceRegistry.class, (p,m,a) -> switch(m.getName()) {
      case "getOwner" -> plugin;
      case "require" -> a[0] == ScheduleService.class ? schedules : inventory[0];
      default -> null;
    });
    inventory[0] = new VexInventoryService(registry);
    return registry;
  }

  private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler));
  }

  private static final class TestMenu implements MutableInventoryView {
    int lowerClicks;
    int page;
    int drags;
    public InventoryKey getKey() { return InventoryKey.of("test:loadout"); }
    public int getSize() { return 54; }
    public Component getTitle(InventoryContext context) { return Component.empty(); }
    public Map<Integer,InventoryElement> getElements(InventoryContext context) { return Map.of(); }
    public void onInventoryClick(InventoryContext context, InventoryClickEvent event) {
      if(event.isCancelled()) return;
      if(event.getRawSlot() >= 54) { lowerClicks++; return; }
      event.setCancelled(true);
      if(event.getRawSlot()==44 && event.getClick()==ClickType.LEFT) page++;
    }
    public void onInventoryDrag(InventoryContext context, InventoryDragEvent event) {
      if(!event.isCancelled()) drags++;
    }
  }
}
