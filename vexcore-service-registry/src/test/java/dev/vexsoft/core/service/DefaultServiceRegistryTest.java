package dev.vexsoft.core.service;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.ServiceNotFoundException;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.ServiceReference;
import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.api.service.VexClassFactory;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import org.junit.jupiter.api.Test;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.Getter;
import static org.junit.jupiter.api.Assertions.*;

class DefaultServiceRegistryTest {

  @Test
  void createsAndResolvesImplementationClasses() {
    VexServiceRegistry services = registry("items");
    services.register(TestService.class, VexTestService.class);
    services.registerQueuedServices();
    assertInstanceOf(VexTestService.class, services.require(TestService.class));
  }

  @Test
  void createsServicesInDependencyOrder() {
    VexServiceRegistry services = registry("skills");
    services.register(DependentService.class, VexDependentService.class);
    services.register(TestService.class, VexTestService.class);
    services.registerQueuedServices();
    assertSame(
        services.require(TestService.class),
        services.require(DependentService.class).getDependency()
    );
  }

  @Test
  void rejectsImplementationsWithoutDependenciesAnnotation() {
    VexServiceRegistry services = registry("invalid");
    assertThrows(
        IllegalArgumentException.class,
        () -> services.register(TestService.class, MissingAnnotationService.class)
    );
  }

  @Test
  void reportsMissingDependencies() {
    VexServiceRegistry services = registry("missing");
    services.register(DependentService.class, VexDependentService.class);
    assertThrows(ServiceNotFoundException.class, services::registerQueuedServices);
  }

  @Test
  void reportsCircularDependencies() {
    VexServiceRegistry services = registry("cycle");
    services.register(CircularOne.class, VexCircularOne.class);
    services.register(CircularTwo.class, VexCircularTwo.class);
    assertThrows(IllegalStateException.class, services::registerQueuedServices);
  }

  @Test
  void rollsBackWhenAConstructorFails() {
    VexServiceRegistry services = registry("rollback");
    services.register(TestService.class, VexTestService.class);
    services.register(FailingService.class, VexFailingService.class);
    assertThrows(IllegalStateException.class, services::registerQueuedServices);
    assertFalse(services.isAvailable(TestService.class));
  }

  @Test
  void ownerBoundServicesWinOverOtherImplementations() {
    DefaultServiceRegistry registry = new DefaultServiceRegistry();
    VexServiceRegistry first = registry.scoped(new TestOwner("first"));
    VexServiceRegistry second = registry.scoped(new TestOwner("second"));
    first.register(TestService.class, VexTestService.class);
    second.register(TestService.class, VexTestService.class);
    first.registerQueuedServices();
    second.registerQueuedServices();
    assertNotSame(first.require(TestService.class), second.require(TestService.class));
  }

  @Test
  void onlyOwnerCanUnregisterItsService() {
    DefaultServiceRegistry registry = new DefaultServiceRegistry();
    VexServiceRegistry owner = registry.scoped(new TestOwner("owner"));
    VexServiceRegistry other = registry.scoped(new TestOwner("other"));
    owner.register(TestService.class, VexTestService.class);
    owner.registerQueuedServices();
    other.unregister(TestService.class);
    assertTrue(owner.isAvailable(TestService.class));
    owner.unregisterOwnedServices();
    assertFalse(owner.isAvailable(TestService.class));
  }

  @Test
  void referenceTracksReplacement() {
    DefaultServiceRegistry registry = new DefaultServiceRegistry();
    VexServiceRegistry first = registry.scoped(new TestOwner("first"));
    ServiceReference<TestService> reference = first.reference(TestService.class);
    first.register(TestService.class, VexTestService.class);
    first.registerQueuedServices();
    TestService firstValue = reference.require();
    first.unregisterOwnedServices();
    assertFalse(reference.isAvailable());
    first.register(TestService.class, VexTestService.class);
    first.registerQueuedServices();
    assertNotSame(firstValue, reference.require());
  }

  @Test
  void requireReportsMissingService() {
    assertThrows(ServiceNotFoundException.class, () -> registry("empty").require(TestService.class));
  }

  @Test
  void createsRegistryAwareComponentsFromClasses() {
    VexServiceRegistry services = registry("components");
    RegistryAwareComponent component = VexClassFactory.create(
        RegistryAwareComponent.class,
        services,
        "Component"
    );
    assertSame(services, component.getServices());
  }

  @Test
  void rejectsRegistryComponentsWithoutDependenciesAnnotation() {
    VexServiceRegistry services = registry("components");
    assertThrows(
        IllegalArgumentException.class,
        () -> VexClassFactory.create(MissingComponentAnnotation.class, services, "Component")
    );
  }

  private VexServiceRegistry registry(final String name) {
    return new DefaultServiceRegistry().scoped(new TestOwner(name));
  }

  @Value
  private static class TestOwner implements ServiceOwner {
    String serviceOwnerName;
  }

  @Dependencies
  @Value
  public static class RegistryAwareComponent {
    VexServiceRegistry services;
  }

  @RequiredArgsConstructor
  public static final class MissingComponentAnnotation {
    private final VexServiceRegistry services;
  }

  private interface TestService extends VexService { }

  @Dependencies
  @RequiredArgsConstructor
  public static final class VexTestService implements TestService {
    private final VexServiceRegistry services;
  }

  @RequiredArgsConstructor
  public static final class MissingAnnotationService implements TestService {
    private final VexServiceRegistry services;
  }

  private interface DependentService extends VexService {
    public TestService getDependency();
  }

  @Dependencies({TestService.class})
  public static final class VexDependentService implements DependentService {
    @Getter
    private final TestService dependency;

    public VexDependentService(final VexServiceRegistry services) {
      this.dependency = services.require(TestService.class);
    }

  }

  private interface FailingService extends VexService { }

  @Dependencies({TestService.class})
  public static final class VexFailingService implements FailingService {
    public VexFailingService(final VexServiceRegistry services) {
      throw new IllegalStateException("expected failure");
    }
  }

  private interface CircularOne extends VexService { }
  private interface CircularTwo extends VexService { }

  @Dependencies({CircularTwo.class})
  @RequiredArgsConstructor
  public static final class VexCircularOne implements CircularOne {
    private final VexServiceRegistry services;
  }

  @Dependencies({CircularOne.class})
  @RequiredArgsConstructor
  public static final class VexCircularTwo implements CircularTwo {
    private final VexServiceRegistry services;
  }
}
