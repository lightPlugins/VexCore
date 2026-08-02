package dev.vexsoft.core.service;

import dev.vexsoft.core.api.service.DuplicateServiceException;
import dev.vexsoft.core.api.service.PluginServiceRegistry;
import dev.vexsoft.core.api.service.ServiceNotFoundException;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.ServiceReference;
import dev.vexsoft.core.api.service.VexService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultServiceRegistryTest {
  @Test
  void registersAndResolves() {
    DefaultServiceRegistry registry = new DefaultServiceRegistry();
    TestOwner owner = new TestOwner("items");
    TestService service = new TestServiceImpl();
    registry.scoped(owner).register(TestService.class, service);
    assertSame(service, registry.require(TestService.class));
  }

  @Test
  void rejectsDuplicates() {
    DefaultServiceRegistry registry = new DefaultServiceRegistry();
    registry.scoped(new TestOwner("first")).register(TestService.class, new TestServiceImpl());
    assertThrows(DuplicateServiceException.class, () ->
        registry.scoped(new TestOwner("second")).register(TestService.class, new TestServiceImpl()));
  }

  @Test
  void onlyOwnerCanUnregister() {
    DefaultServiceRegistry registry = new DefaultServiceRegistry();
    PluginServiceRegistry owner = registry.scoped(new TestOwner("owner"));
    PluginServiceRegistry other = registry.scoped(new TestOwner("other"));
    owner.register(TestService.class, new TestServiceImpl());
    other.unregister(TestService.class);
    assertTrue(owner.isAvailable(TestService.class));
    owner.unregisterOwnedServices();
    assertFalse(owner.isAvailable(TestService.class));
  }

  @Test
  void referenceTracksReplacement() {
    DefaultServiceRegistry registry = new DefaultServiceRegistry();
    PluginServiceRegistry first = registry.scoped(new TestOwner("first"));
    ServiceReference<TestService> reference = first.reference(TestService.class);
    TestService firstValue = new TestServiceImpl();
    first.register(TestService.class, firstValue);
    assertSame(firstValue, reference.require());
    first.unregisterOwnedServices();
    assertFalse(reference.isAvailable());
    TestService secondValue = new TestServiceImpl();
    registry.scoped(new TestOwner("second")).register(TestService.class, secondValue);
    assertSame(secondValue, reference.require());
  }

  @Test
  void requireReportsMissingService() {
    assertThrows(ServiceNotFoundException.class,
        () -> new DefaultServiceRegistry().require(TestService.class));
  }

  private record TestOwner(String serviceOwnerName) implements ServiceOwner { }
  private interface TestService extends VexService { }
  private static final class TestServiceImpl implements TestService { }
}
