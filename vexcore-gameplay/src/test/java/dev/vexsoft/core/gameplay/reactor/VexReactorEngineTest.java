package dev.vexsoft.core.gameplay.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.cache.CacheService;
import dev.vexsoft.core.cache.VexCacheService;
import dev.vexsoft.core.gameplay.reactor.condition.ConditionRegistry;
import dev.vexsoft.core.gameplay.reactor.context.ReactorContext;
import dev.vexsoft.core.gameplay.reactor.effect.CompiledEffect;
import dev.vexsoft.core.gameplay.reactor.effect.Effect;
import dev.vexsoft.core.gameplay.reactor.effect.EffectRegistry;
import dev.vexsoft.core.gameplay.reactor.filter.FilterRegistry;
import dev.vexsoft.core.gameplay.reactor.registry.ReactorRegistryCoordinatorService;
import dev.vexsoft.core.gameplay.reactor.registry.VexConditionRegistry;
import dev.vexsoft.core.gameplay.reactor.registry.VexEffectRegistry;
import dev.vexsoft.core.gameplay.reactor.registry.VexFilterRegistry;
import dev.vexsoft.core.gameplay.reactor.registry.VexReactorRegistryCoordinatorService;
import dev.vexsoft.core.gameplay.reactor.registry.VexTriggerRegistry;
import dev.vexsoft.core.gameplay.reactor.trigger.Trigger;
import dev.vexsoft.core.gameplay.reactor.trigger.TriggerRegistry;
import dev.vexsoft.core.service.DefaultServiceRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VexReactorEngineTest {

  private static final AtomicInteger EXECUTIONS = new AtomicInteger();

  private ReactorEngine reactions;
  private EffectRegistry effects;

  @BeforeEach
  void setUp() {
    EXECUTIONS.set(0);
    VexServiceRegistry infrastructure = new DefaultServiceRegistry().scoped(new Owner("runtime"));
    infrastructure.register(CacheService.class, VexCacheService.class);
    infrastructure.register(
        ReactorRegistryCoordinatorService.class,
        VexReactorRegistryCoordinatorService.class
    );
    infrastructure.registerQueuedServices();

    VexServiceRegistry plugin = infrastructure.scoped(new Owner("test-plugin"));
    plugin.register(TriggerRegistry.class, VexTriggerRegistry.class);
    plugin.register(FilterRegistry.class, VexFilterRegistry.class);
    plugin.register(ConditionRegistry.class, VexConditionRegistry.class);
    plugin.register(EffectRegistry.class, VexEffectRegistry.class);
    plugin.register(ReactorEngine.class, VexReactorEngine.class);
    plugin.registerQueuedServices();
    plugin.require(TriggerRegistry.class).register(TestTrigger.class);
    effects = plugin.require(EffectRegistry.class);
    effects.register(CountingEffect.class);
    effects.register(FailingEffect.class);
    reactions = plugin.require(ReactorEngine.class);
  }

  @Test
  void keepsPreviousSnapshotWhenReloadCompilationFails() {
    reactions.reload(List.of(reaction("valid", "count")));
    reactions.dispatch("test-trigger", new TestContext(4));
    assertEquals(1, EXECUTIONS.get());

    assertThrows(IllegalArgumentException.class, () -> reactions.reload(List.of(
        reaction("invalid", "missing-effect")
    )));
    reactions.dispatch("test-trigger", new TestContext(4));

    assertEquals(2, EXECUTIONS.get());
  }

  @Test
  void stopsFailingReactionButContinuesWithOtherReactions() {
    ReactionDefinition failing = ReactionDefinition.builder()
        .id("failing")
        .trigger("test-trigger")
        .effect(component("fail"))
        .effect(component("count"))
        .build();
    reactions.reload(List.of(failing, reaction("healthy", "count")));

    reactions.dispatch("test-trigger", new TestContext(2));

    assertEquals(1, EXECUTIONS.get());
  }

  private static ReactionDefinition reaction(final String id, final String effect) {
    return ReactionDefinition.builder()
        .id(id)
        .trigger("test-trigger")
        .effect(component(effect))
        .build();
  }

  private static ReactionComponentDefinition component(final String id) {
    return ReactionComponentDefinition.builder().id(id).build();
  }

  private record TestContext(int value) implements ReactorContext {
    @Override
    public Object getVariable(final String name) {
      return "value".equals(name) ? value : null;
    }
  }

  @ReactorId("test-trigger")
  @Dependencies
  public static final class TestTrigger implements Trigger<TestContext> {
    public TestTrigger(final VexServiceRegistry services) { }

    @Override
    public Class<TestContext> getContextType() {
      return TestContext.class;
    }
  }

  @ReactorId("count")
  @Dependencies
  public static final class CountingEffect implements Effect<ReactorContext> {
    public CountingEffect(final VexServiceRegistry services) { }

    @Override
    public Class<ReactorContext> getContextType() {
      return ReactorContext.class;
    }

    @Override
    public CompiledEffect<ReactorContext> compile(final Map<String, Object> arguments) {
      return ignored -> EXECUTIONS.incrementAndGet();
    }
  }

  @ReactorId("fail")
  @Dependencies
  public static final class FailingEffect implements Effect<ReactorContext> {
    public FailingEffect(final VexServiceRegistry services) { }

    @Override
    public Class<ReactorContext> getContextType() {
      return ReactorContext.class;
    }

    @Override
    public CompiledEffect<ReactorContext> compile(final Map<String, Object> arguments) {
      return ignored -> {
        throw new IllegalStateException("expected");
      };
    }
  }

  private record Owner(String getServiceOwnerName) implements ServiceOwner { }
}
