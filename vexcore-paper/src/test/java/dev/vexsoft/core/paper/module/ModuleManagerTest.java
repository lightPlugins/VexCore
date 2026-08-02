package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.service.DefaultServiceRegistry;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuleManagerTest {

  @Test
  void disablesRemainingModulesWhenOneModuleFails() {
    ModuleManager manager = new ModuleManager(new DefaultServiceRegistry());
    List<String> disabled = new ArrayList<>();
    manager.enable(new TestModule("first", disabled, false));
    manager.enable(new TestModule("second", disabled, true));
    manager.enable(new TestModule("third", disabled, false));

    assertThrows(IllegalStateException.class, manager::disableAll);
    assertEquals(List.of("third", "second", "first"), disabled);
  }

  private static final class TestModule implements VexModule {
    private final String name;
    private final List<String> disabled;
    private final boolean fail;

    private TestModule(String name, List<String> disabled, boolean fail) {
      this.name = name;
      this.disabled = disabled;
      this.fail = fail;
    }

    @Override
    public void enable(ServiceRegistry services) {
    }

    @Override
    public void disable() {
      disabled.add(name);
      if (fail) {
        throw new IllegalStateException("Expected test failure");
      }
    }

    @Override
    public String serviceOwnerName() {
      return name;
    }
  }
}
