package dev.vexsoft.core.paper.service.screenui;

import static org.junit.jupiter.api.Assertions.*;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.screenui.TextBlockLayout;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

public final class ScreenUiLifecycleTest {
  @Test public void coalescesUpdatesAndSeparatesPluginOwners() {
    Harness test = new Harness();
    ServiceOwner first = () -> "first";
    ServiceOwner second = () -> "second";
    var a = test.coordinator.open(first, test.player, "same-id");
    var b = test.coordinator.open(second, test.player, "same-id");
    a.textBlock("text", List.of(Component.text("A")), TextBlockLayout.builder().build());
    for (int i = 0; i < 100; i++) a.setLines("text", List.of(Component.text("Update " + i)));
    b.textBlock("text", List.of(Component.text("B")), TextBlockLayout.builder().y(20).build());
    assertEquals(1, test.queued.size());
    test.queued.removeFirst().run();
    assertEquals(1, test.shows);
    test.coordinator.closeOwner(first);
    assertTrue(a.isClosed());
    assertFalse(b.isClosed());
    assertTrue(test.coordinator.find(second, test.player, "same-id").isPresent());
    test.queued.removeFirst().run();
    assertEquals(0, test.hides);
    b.close();
    assertEquals(1, test.hides);
  }

  @Test public void lateCallbackCannotResurrectClosedUi() {
    Harness test = new Harness();
    var ui = test.coordinator.open(() -> "test", test.player, "demo");
    ui.textBlock("text", List.of(Component.text("A")), TextBlockLayout.builder().build());
    test.coordinator.discard(test.player);
    test.queued.removeFirst().run();
    assertTrue(ui.isClosed());
    assertEquals(0, test.shows);
    assertThrows(IllegalStateException.class, () -> ui.setLines("text", List.of(Component.text("B"))));
  }

  public static final class Harness {
    private final List<Runnable> queued = new ArrayList<>();
    private int shows;
    private int hides;
    private final Player player;
    private final VexScreenUiCoordinatorService coordinator;
    private Harness() {
      UUID id = UUID.randomUUID();
      player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
          (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "isOnline" -> true;
            case "showBossBar" -> { shows++; yield null; }
            case "hideBossBar" -> { hides++; yield null; }
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "UiTestPlayer";
            default -> throw new UnsupportedOperationException(method.getName());
          });
      VexTask task = (VexTask) Proxy.newProxyInstance(VexTask.class.getClassLoader(), new Class<?>[]{VexTask.class},
          (proxy, method, args) -> method.getName().equals("cancel") ? null : false);
      ScheduleService schedules = (ScheduleService) Proxy.newProxyInstance(ScheduleService.class.getClassLoader(),
          new Class<?>[]{ScheduleService.class}, (proxy, method, args) -> {
            if (!method.getName().equals("runForLater")) throw new UnsupportedOperationException(method.getName());
            queued.add((Runnable) args[2]);
            return Optional.of(task);
          });
      VexServiceRegistry registry = (VexServiceRegistry) Proxy.newProxyInstance(VexServiceRegistry.class.getClassLoader(),
          new Class<?>[]{VexServiceRegistry.class}, (proxy, method, args) -> args[0] == ScheduleService.class ? schedules : new dev.vexsoft.core.paper.screenui.v26_2.V26_2ScreenUiVersionDefinition());
      coordinator = new VexScreenUiCoordinatorService(registry);
    }
  }
}

