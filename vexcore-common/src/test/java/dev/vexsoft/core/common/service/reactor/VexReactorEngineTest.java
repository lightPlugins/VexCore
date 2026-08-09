package dev.vexsoft.core.common.service.reactor;

import dev.vexsoft.core.reactor.ReactionComponentDefinition;
import dev.vexsoft.core.reactor.ReactionDefinition;
import dev.vexsoft.core.reactor.ReactionTriggerDefinition;
import dev.vexsoft.core.reactor.ReactorId;

import dev.vexsoft.core.api.service.reactor.ReactorEngine;
import dev.vexsoft.core.api.service.reactor.ReactionConfigurationService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.common.service.cache.VexCacheService;
import dev.vexsoft.core.common.configuration.ConfigurateConfigurationSection;
import dev.vexsoft.core.api.service.reactor.ConditionRegistry;
import dev.vexsoft.core.reactor.context.ReactorContext;
import dev.vexsoft.core.reactor.effect.CompiledEffect;
import dev.vexsoft.core.reactor.effect.Effect;
import dev.vexsoft.core.api.service.reactor.EffectRegistry;
import dev.vexsoft.core.api.service.reactor.FilterRegistry;
import dev.vexsoft.core.reactor.trigger.Trigger;
import dev.vexsoft.core.api.service.reactor.TriggerRegistry;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import dev.vexsoft.core.api.service.stats.StatRegistry;
import dev.vexsoft.core.common.service.stats.StatRegistryCoordinatorService;
import dev.vexsoft.core.common.service.stats.VexStatRegistry;
import dev.vexsoft.core.common.service.stats.VexStatRegistryCoordinatorService;
import dev.vexsoft.core.stats.StatDefinition;
import dev.vexsoft.core.stats.StatKey;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;

class VexReactorEngineTest {

  private static final AtomicInteger EXECUTIONS = new AtomicInteger();

  private ReactorEngine reactions;
  private EffectRegistry effects;
  private ReactionConfigurationService configurations;
  private StatRegistry stats;

  @BeforeEach
  void setUp() {
    EXECUTIONS.set(0);
    VexServiceRegistry infrastructure = new DefaultServiceRegistry().scoped(new Owner("runtime"));
    infrastructure.register(CacheService.class, VexCacheService.class);
    infrastructure.register(
        ReactorRegistryCoordinatorService.class,
        VexReactorRegistryCoordinatorService.class
    );
    infrastructure.register(
        StatRegistryCoordinatorService.class,
        VexStatRegistryCoordinatorService.class
    );
    infrastructure.registerQueuedServices();

    VexServiceRegistry plugin = infrastructure.scoped(new Owner("test-plugin"));
    plugin.register(TriggerRegistry.class, VexTriggerRegistry.class);
    plugin.register(FilterRegistry.class, VexFilterRegistry.class);
    plugin.register(ConditionRegistry.class, VexConditionRegistry.class);
    plugin.register(EffectRegistry.class, VexEffectRegistry.class);
    plugin.register(ReactorEngine.class, VexReactorEngine.class);
    plugin.register(StatRegistry.class, VexStatRegistry.class);
    plugin.register(
        ReactionConfigurationService.class,
        VexReactionConfigurationService.class
    );
    plugin.registerQueuedServices();
    plugin.require(TriggerRegistry.class).register(TestTrigger.class);
    effects = plugin.require(EffectRegistry.class);
    effects.register(CountingEffect.class);
    effects.register(FailingEffect.class);
    reactions = plugin.require(ReactorEngine.class);
    configurations = plugin.require(ReactionConfigurationService.class);
    stats = plugin.require(StatRegistry.class);
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
        .trigger(ReactionTriggerDefinition.builder().id("test-trigger").build())
        .effect(component("fail"))
        .effect(component("count"))
        .build();
    reactions.reload(List.of(failing, reaction("healthy", "count")));

    reactions.dispatch("test-trigger", new TestContext(2));

    assertEquals(1, EXECUTIONS.get());
  }

  @Test
  void loadsTriggerSpecificFiltersAndGlobalComponents() throws Exception {
    CommentedConfigurationNode root = CommentedConfigurationNode.root();
    root.node("xp-gain-methods").set(List.of(Map.of(
        "triggers", List.of(
            Map.of("id", "test-trigger", "filters", Map.of("blocks", List.of("minecraft:stone"))),
            Map.of("id", "test-trigger")
        ),
        "conditions", List.of(Map.of("id", "chance", "args", Map.of("chance", 0.5D))),
        "effects", List.of(Map.of("id", "count", "args", Map.of("amount", 2)))
    )));

    ReactionDefinition loaded = configurations.load(
        new ConfigurateConfigurationSection(root),
        "xp-gain-methods"
    ).getFirst();

    assertEquals("xp-gain-methods-1", loaded.getId());
    assertEquals(List.of("minecraft:stone"), loaded.getTriggers().getFirst()
        .getFilters().get("blocks"));
    assertEquals(Map.of(), loaded.getTriggers().get(1).getFilters());
    assertEquals("chance", loaded.getConditions().getFirst().getId());
    assertEquals("count", loaded.getEffects().getFirst().getId());
  }

  @Test
  void restoresStatsWhenReactionCompilationFails() throws Exception {
    StatKey power = StatKey.of("test_plugin", "power");
    stats.synchronize(List.of(StatDefinition.builder(power).defaultValue(5D).build()));
    CommentedConfigurationNode root = CommentedConfigurationNode.root();
    root.node("reactions").set(List.of(Map.of(
        "triggers", List.of(Map.of("id", "test-trigger")),
        "effects", List.of(Map.of("id", "missing-effect"))
    )));

    assertThrows(IllegalArgumentException.class, () -> configurations.reload(
        new ConfigurateConfigurationSection(root),
        "reactions",
        List.of(StatDefinition.builder(power).defaultValue(12D).build())
    ));

    assertEquals(5D, stats.require(power).getDefinition().getDefaultValue());
  }

  private static ReactionDefinition reaction(final String id, final String effect) {
    return ReactionDefinition.builder()
        .id(id)
        .trigger(ReactionTriggerDefinition.builder().id("test-trigger").build())
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
