package dev.vexsoft.core.paper.dialog;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.dialog.DialogResultType;
import dev.vexsoft.core.service.DefaultServiceRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class VexDialogCoordinatorServiceTest {

  @Test
  public void replacesThePreviousPlayerSession() {
    VexDialogCoordinatorService coordinator = coordinator();
    ServiceOwner firstOwner = owner("first");
    ServiceOwner secondOwner = owner("second");
    UUID playerId = UUID.randomUUID();

    DialogSession<String> first = coordinator.begin(firstOwner, playerId);
    DialogSession<Boolean> second = coordinator.begin(secondOwner, playerId);

    assertEquals(DialogResultType.REPLACED, first.getFuture().join().getType());
    assertTrue(coordinator.isActive(second));
    assertFalse(coordinator.isActive(first));
  }

  @Test
  public void closesOnlySessionsBelongingToTheOwner() {
    VexDialogCoordinatorService coordinator = coordinator();
    ServiceOwner firstOwner = owner("first");
    ServiceOwner secondOwner = owner("second");
    DialogSession<String> first = coordinator.begin(firstOwner, UUID.randomUUID());
    DialogSession<String> second = coordinator.begin(secondOwner, UUID.randomUUID());

    coordinator.closeOwned(firstOwner, DialogResultType.PLUGIN_DISABLED);

    assertEquals(DialogResultType.PLUGIN_DISABLED, first.getFuture().join().getType());
    assertFalse(second.getFuture().isDone());
  }

  private VexDialogCoordinatorService coordinator() {
    DefaultServiceRegistry registry = new DefaultServiceRegistry();
    VexServiceRegistry services = registry.scoped(owner("test"));
    return new VexDialogCoordinatorService(services);
  }

  private ServiceOwner owner(final String name) {
    return () -> name;
  }
}
