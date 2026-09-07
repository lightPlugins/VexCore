package dev.vexsoft.core.paper.service.inventory;

import static org.junit.jupiter.api.Assertions.*;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.inventory.*;
import dev.vexsoft.core.paper.inventory.element.*;
import dev.vexsoft.core.paper.inventory.page.*;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.stream.IntStream;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

public final class VexSlotInventoryInteractionsTest {
  @Test void ordinaryLowerClicksAndDragsStayNative() {
    Fixture f = new Fixture();
    for (ClickType type : List.of(ClickType.LEFT, ClickType.RIGHT, ClickType.NUMBER_KEY)) {
      InventoryClickEvent click = f.click(60, type);
      f.handler.click(f.context, f.menu, f.elements(), click);
      assertFalse(click.isCancelled());
    }
    var drag = f.drag(Set.of(60,61));
    f.handler.drag(f.context,f.menu,f.elements(),drag);
    assertFalse(drag.isCancelled());
  }

  @Test void shiftMovesBetweenBackpackAndHotbarWithoutTouchingMenu() {
    Fixture f = new Fixture();
    f.storage[9] = new Stack("ore",10);
    f.storage[0] = new Stack("ore",60);
    var click = f.click(63,ClickType.SHIFT_LEFT);
    f.handler.click(f.context,f.menu,f.elements(),click);
    assertTrue(click.isCancelled());
    assertNull(f.storage[9]);
    assertEquals(64,f.storage[0].getAmount());
    assertEquals(6,f.storage[1].getAmount());
    assertEquals(0,f.menu.transfers);
  }

  @Test void doubleClickCollectsOnlyRealInventoryStacks() {
    Fixture f = new Fixture();
    f.cursor = new Stack("ore",1);
    f.storage[3] = new Stack("ore",8);
    var click = f.click(57,ClickType.DOUBLE_CLICK);
    f.handler.click(f.context,f.menu,f.elements(),click);
    assertTrue(click.isCancelled());
    assertEquals(9,f.cursor.getAmount());
    assertNull(f.storage[3]);
    assertEquals(0,f.menu.transfers);
  }

  @Test void exchangeUsesBackingItemsAndNeverTheRenderedPlaceholder() {
    Fixture f = new Fixture();
    f.cursor = new Stack("helmet",1);
    f.handler.click(f.context,f.menu,f.elements(),f.click(11,ClickType.LEFT));
    assertTrue(f.cursor.isEmpty());
    assertEquals("helmet",((Stack)f.menu.stored).id);
    f.handler.click(f.context,f.menu,f.elements(),f.click(11,ClickType.LEFT));
    assertEquals("helmet",((Stack)f.cursor).id);
    assertTrue(f.menu.stored.isEmpty());
    assertEquals(2,f.menu.transfers);
  }

  @Test void singleSlotDragRunsAfterCursorRestoreAndClosingAbortsIt() {
    Fixture f = new Fixture();
    f.cursor = new Stack("helmet",1);
    var drag = f.drag(Set.of(11));
    f.handler.drag(f.context,f.menu,f.elements(),drag);
    assertTrue(drag.isCancelled());
    assertEquals(0,f.menu.transfers);
    f.runScheduled();
    assertEquals(1,f.menu.transfers);
    f.cursor = new Stack("helmet",1);
    f.handler.drag(f.context,f.menu,f.elements(),f.drag(Set.of(11)));
    f.handler.cancel(f.id);
    f.runScheduled();
    assertEquals(1,f.menu.transfers);
  }

  @Test void rejectedSchedulingDoesNotBlockFurtherInventoryClicks() {
    Fixture f = new Fixture();
    f.rejectScheduling = true;
    f.handler.drag(f.context,f.menu,f.elements(),f.drag(Set.of(11)));
    var click = f.click(60,ClickType.LEFT);
    f.handler.click(f.context,f.menu,f.elements(),click);
    assertFalse(click.isCancelled());
    assertEquals(0,f.menu.transfers);
  }

  @Test void mixedDragAndProtectedClicksNeverPartlyTransfer() {
    Fixture f = new Fixture();
    var mixed = f.drag(Set.of(11,60));
    f.handler.drag(f.context,f.menu,f.elements(),mixed);
    assertTrue(mixed.isCancelled());
    var click = f.click(11,ClickType.LEFT);
    click.setCancelled(true);
    f.handler.click(f.context,f.menu,f.elements(),click);
    assertEquals(0,f.menu.transfers);
    assertTrue(f.scheduled.isEmpty());
  }

  @Test void existingPaginationShowsTwelveThenSixEntriesAndAcceptsOnlyLeftClick() {
    Fixture f = new Fixture();
    assertEquals(2,f.menu.getPageCount(f.context));
    for(ClickType type:List.of(ClickType.RIGHT,ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY,ClickType.SWAP_OFFHAND)) {
      var click=f.click(44,type);
      f.handler.click(f.context,f.menu,f.elements(),click);
      assertEquals(0,f.menu.getPage());
      assertTrue(click.isCancelled());
    }
    f.handler.click(f.context,f.menu,f.elements(),f.click(44,ClickType.LEFT));
    assertEquals(1,f.menu.getPage());
    assertEquals(13,((Entry)f.elements().get(14)).number);
    assertEquals(18,((Entry)f.elements().get(25)).number);
    assertFalse(f.elements().containsKey(32));
    f.handler.click(f.context,f.menu,f.elements(),f.click(17,ClickType.LEFT));
    assertEquals(0,f.menu.getPage());
  }

  private static final class Fixture {
    final UUID id=UUID.randomUUID();
    final ItemStack[] storage=new ItemStack[36];
    ItemStack cursor=new Stack("empty",0);
    boolean rejectScheduling;
    final List<Runnable> scheduled=new ArrayList<>();
    final Inventory top=proxy(Inventory.class,(p,m,a)-> m.getName().equals("getSize")?54:null);
    final PlayerInventory inventory=proxy(PlayerInventory.class,(p,m,a)-> switch(m.getName()) {
      case "getItem" -> storage[(int)a[0]];
      case "setItem" -> { storage[(int)a[0]]=(ItemStack)a[1]; yield null; }
      default -> null;
    });
    final Player player=proxy(Player.class,(p,m,a)-> switch(m.getName()) {
      case "getUniqueId" -> id;
      case "getInventory" -> inventory;
      case "getItemOnCursor" -> cursor;
      case "setItemOnCursor" -> { cursor=(ItemStack)a[0]; yield null; }
      case "getOpenInventory" -> this.view;
      default -> null;
    });
    final org.bukkit.inventory.InventoryView view=proxy(org.bukkit.inventory.InventoryView.class,(p,m,a)-> switch(m.getName()) {
      case "getPlayer" -> player;
      case "getTopInventory" -> top;
      case "getInventory" -> (int)a[0]>=54?inventory:top;
      case "convertSlot" -> (int)a[0]>=54?(int)a[0]-54:(int)a[0];
      default -> null;
    });
    final ScheduleService schedules=proxy(ScheduleService.class,(p,m,a)-> { if(m.getName().equals("runForLater")) { if(rejectScheduling) return Optional.empty(); scheduled.add((Runnable)a[2]); return Optional.of(proxy(dev.vexsoft.core.paper.scheduler.VexTask.class,(task,method,args)->null)); } return null; });
    final VexServiceRegistry registry=proxy(VexServiceRegistry.class,(p,m,a)->schedules);
    final Menu menu=new Menu(registry);
    final InventoryService service=proxy(InventoryService.class,(p,m,a)-> m.getName().equals("getCurrentView")?Optional.of(menu):null);
    final InventoryContext context=new InventoryContext(registry,player,service);
    final VexSlotInventoryInteractions handler=new VexSlotInventoryInteractions(schedules);
    Map<Integer,InventoryElement> elements() { return menu.getElements(context); }
    InventoryClickEvent click(int slot,ClickType type) {
      return new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,slot,type,InventoryAction.PICKUP_ALL);
    }
    InventoryDragEvent drag(Set<Integer> slots) {
      Map<Integer,ItemStack> contents=new HashMap<>(); slots.forEach(i->contents.put(i,new Stack("helmet",1)));
      return new InventoryDragEvent(view,null,cursor,false,contents);
    }
    void runScheduled() { List.copyOf(scheduled).forEach(Runnable::run); scheduled.clear(); }
  }

  private static final class Menu extends PagedInventoryView<Integer> implements SlotInventoryView {
    int transfers;
    ItemStack stored=new Stack("empty",0);
    Menu(VexServiceRegistry registry) {
      super(registry,InventoryKey.of("test:slots"),54,PageBounds.rectangle(5,1,3,4),
          context->IntStream.rangeClosed(1,18).boxed().toList());
      addElement(11,new CursorInventoryElement(context->new Stack("placeholder",1),context->{
        ItemStack incoming=context.getViewer().getItemOnCursor();
        context.getViewer().setItemOnCursor(stored); stored=incoming; transfers++;
      }));
      setPreviousButton(17,context->null);
      setNextButton(44,context->null);
    }
    @Override protected InventoryElement renderPageItem(InventoryContext context,Integer number,int index) { return new Entry(number); }
  }
  private static final class Entry extends RefreshableInventoryElement {
    final int number;
    Entry(int number) { super(context->null); this.number=number; }
  }
  /** Minimal stack double: no Bukkit server or item factory is involved in routing tests. */
  private static final class Stack extends ItemStack {
    final String id; int amount;
    Stack(String id,int amount) { this.id=id; this.amount=amount; }
    @Override public ItemStack clone() { return new Stack(id,amount); }
    @Override public int getAmount() { return amount; }
    @Override public void setAmount(int value) { amount=value; }
    @Override public int getMaxStackSize() { return 64; }
    @Override public boolean isEmpty() { return amount==0; }
    @Override public boolean isSimilar(ItemStack other) { return other instanceof Stack s && id.equals(s.id); }
    @Override public boolean equals(Object other) { return other instanceof Stack s && id.equals(s.id) && amount==s.amount; }
    @Override public int hashCode() { return Objects.hash(id,amount); }
  }
  private static <T> T proxy(Class<T> type,java.lang.reflect.InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler));
  }
}
