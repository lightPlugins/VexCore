package dev.vexsoft.core.gameplay.reactor.registry;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexClassFactory;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.cache.CacheService;
import dev.vexsoft.core.cache.VexCache;
import dev.vexsoft.core.cache.VexCacheOptions;
import dev.vexsoft.core.gameplay.reactor.ReactionComponentDefinition;
import dev.vexsoft.core.gameplay.reactor.ReactionDefinition;
import dev.vexsoft.core.gameplay.reactor.ReactorId;
import dev.vexsoft.core.gameplay.reactor.condition.CompiledCondition;
import dev.vexsoft.core.gameplay.reactor.condition.Condition;
import dev.vexsoft.core.gameplay.reactor.context.ReactorContext;
import dev.vexsoft.core.gameplay.reactor.effect.CompiledEffect;
import dev.vexsoft.core.gameplay.reactor.effect.Effect;
import dev.vexsoft.core.gameplay.reactor.execution.CompiledReaction;
import dev.vexsoft.core.gameplay.reactor.filter.CompiledFilter;
import dev.vexsoft.core.gameplay.reactor.filter.Filter;
import dev.vexsoft.core.gameplay.reactor.trigger.Trigger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Default coordinator for class-based reaction components and atomic runtime snapshots. */
@Dependencies(CacheService.class)
public final class VexReactorRegistryCoordinatorService implements
    ReactorRegistryCoordinatorService,
    AutoCloseable {

  private static final System.Logger LOGGER = System.getLogger(
      VexReactorRegistryCoordinatorService.class.getName()
  );
  private static final long ERROR_INTERVAL_NANOS = Duration.ofSeconds(30).toNanos();
  private static final String ERROR_CACHE_NAME = "reactor-runtime-errors";

  private final Map<String, RegisteredReactorComponent<Trigger<?>>> triggers = new LinkedHashMap<>();
  private final Map<String, RegisteredReactorComponent<Filter<?>>> filters = new LinkedHashMap<>();
  private final Map<String, RegisteredReactorComponent<Condition<?>>> conditions =
      new LinkedHashMap<>();
  private final Map<String, RegisteredReactorComponent<Effect<?>>> effects = new LinkedHashMap<>();
  private final Map<ServiceOwner, List<TriggerBinding>> ownerReactions = new IdentityHashMap<>();
  private final CacheService caches;
  private final VexCache<String, Long> reportedErrors;
  private volatile Map<String, TriggerPlan> runtime = Map.of();

  /** Creates a coordinator using the shared bounded cache service for error throttling. */
  public VexReactorRegistryCoordinatorService(final VexServiceRegistry services) {
    caches = Objects.requireNonNull(services, "services").require(CacheService.class);
    reportedErrors = caches.create(
            ERROR_CACHE_NAME,
            VexCacheOptions.builder()
                .maximumSize(1_000L)
                .expireAfterAccess(Duration.ofMinutes(5))
                .recordStats(false)
                .build()
        );
  }

  @Override
  public synchronized void close() {
    runtime = Map.of();
    ownerReactions.clear();
    triggers.clear();
    filters.clear();
    conditions.clear();
    effects.clear();
    caches.destroy(ERROR_CACHE_NAME);
  }

  @Override
  public synchronized void register(
      final ServiceOwner owner,
      final VexServiceRegistry services,
      final ReactorComponentKind kind,
      final Class<?> componentType
  ) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(services, "services");
    Class<?> checkedType = Objects.requireNonNull(componentType, "componentType");
    String id = componentId(checkedType);
    requireAvailable(kind, id);
    Object component = VexClassFactory.create(checkedType, services, kind.name());
    switch (kind) {
      case TRIGGER -> register(triggers, id, owner, Trigger.class.cast(component));
      case FILTER -> register(filters, id, owner, Filter.class.cast(component));
      case CONDITION -> register(conditions, id, owner, Condition.class.cast(component));
      case EFFECT -> register(effects, id, owner, Effect.class.cast(component));
      default -> throw new IllegalStateException("Unsupported reaction component kind: " + kind);
    }
  }

  @Override
  public synchronized void unregisterOwner(
      final ServiceOwner owner,
      final ReactorComponentKind kind
  ) {
    Objects.requireNonNull(owner, "owner");
    switch (kind) {
      case TRIGGER -> removeOwned(triggers, owner);
      case FILTER -> removeOwned(filters, owner);
      case CONDITION -> removeOwned(conditions, owner);
      case EFFECT -> removeOwned(effects, owner);
      default -> throw new IllegalStateException("Unsupported reaction component kind: " + kind);
    }
  }

  @Override
  public synchronized void reload(
      final ServiceOwner owner,
      final Collection<ReactionDefinition> definitions
  ) {
    Objects.requireNonNull(owner, "owner");
    List<TriggerBinding> compiled = compile(owner, definitions);
    ownerReactions.put(owner, compiled);
    publishRuntime();
  }

  @Override
  public synchronized void clear(final ServiceOwner owner) {
    if (ownerReactions.remove(Objects.requireNonNull(owner, "owner")) != null) {
      publishRuntime();
    }
  }

  @Override
  public void dispatch(final String triggerId, final ReactorContext context) {
    String checkedId = Objects.requireNonNull(triggerId, "triggerId");
    ReactorContext checkedContext = Objects.requireNonNull(context, "context");
    TriggerPlan plan = runtime.get(checkedId);
    if (plan == null) {
      return;
    }
    if (!plan.contextType.isInstance(checkedContext)) {
      throw new IllegalArgumentException(
          "Trigger '" + checkedId + "' requires context " + plan.contextType.getName()
      );
    }
    for (CompiledReaction reaction : plan.reactions) {
      execute(checkedId, reaction, checkedContext);
    }
  }

  private List<TriggerBinding> compile(
      final ServiceOwner owner,
      final Collection<ReactionDefinition> definitions
  ) {
    Collection<ReactionDefinition> checkedDefinitions = Objects.requireNonNull(
        definitions,
        "definitions"
    );
    Map<String, ReactionDefinition> unique = new LinkedHashMap<>();
    List<TriggerBinding> result = new ArrayList<>();
    for (ReactionDefinition definition : checkedDefinitions) {
      ReactionDefinition checked = Objects.requireNonNull(definition, "definition");
      String reactionId = requireId(checked.getId(), "reaction");
      if (unique.putIfAbsent(reactionId, checked) != null) {
        throw new IllegalArgumentException("Duplicate reaction ID: " + reactionId);
      }
      if (checked.isEnabled()) {
        compileReaction(owner, checked, reactionId, result);
      }
    }
    return List.copyOf(result);
  }

  private void compileReaction(
      final ServiceOwner owner,
      final ReactionDefinition definition,
      final String reactionId,
      final List<TriggerBinding> result
  ) {
    if (definition.getTriggers().isEmpty()) {
      throw new IllegalArgumentException("Reaction '" + reactionId + "' has no triggers");
    }
    for (String triggerIdValue : definition.getTriggers()) {
      String triggerId = requireId(triggerIdValue, "trigger");
      Trigger<?> trigger = require(triggers, triggerId, "trigger").getComponent();
      Class<? extends ReactorContext> contextType = trigger.getContextType();
      result.add(new TriggerBinding(
          triggerId,
          contextType,
          compileReactionForTrigger(owner, definition, reactionId, contextType)
      ));
    }
  }

  @SuppressWarnings("unchecked")
  private CompiledReaction compileReactionForTrigger(
      final ServiceOwner owner,
      final ReactionDefinition definition,
      final String reactionId,
      final Class<? extends ReactorContext> contextType
  ) {
    List<CompiledFilter<ReactorContext>> compiledFilters = new ArrayList<>();
    for (Map.Entry<String, Object> entry : definition.getFilters().entrySet()) {
      String id = requireId(entry.getKey(), "filter");
      Filter<?> filter = require(filters, id, "filter").getComponent();
      requireCompatible(reactionId, contextType, filter.getContextType(), "filter", id);
      compiledFilters.add(compileFilter(filter, entry.getValue()));
    }
    List<CompiledCondition<ReactorContext>> compiledConditions = new ArrayList<>();
    for (ReactionComponentDefinition conditionDefinition : definition.getConditions()) {
      String id = requireId(conditionDefinition.getId(), "condition");
      Condition<?> condition = require(conditions, id, "condition").getComponent();
      requireCompatible(reactionId, contextType, condition.getContextType(), "condition", id);
      compiledConditions.add(compileCondition(condition, conditionDefinition.getArguments()));
    }
    List<CompiledReaction.NamedEffect> compiledEffects = new ArrayList<>();
    for (ReactionComponentDefinition effectDefinition : definition.getEffects()) {
      String id = requireId(effectDefinition.getId(), "effect");
      Effect<?> effect = require(effects, id, "effect").getComponent();
      requireCompatible(reactionId, contextType, effect.getContextType(), "effect", id);
      compiledEffects.add(new CompiledReaction.NamedEffect(
          id,
          compileEffect(effect, effectDefinition.getArguments())
      ));
    }
    return new CompiledReaction(
        owner.getServiceOwnerName(),
        reactionId,
        compiledFilters.toArray(CompiledFilter[]::new),
        compiledConditions.toArray(CompiledCondition[]::new),
        compiledEffects.toArray(CompiledReaction.NamedEffect[]::new)
    );
  }

  @SuppressWarnings("unchecked")
  private CompiledFilter<ReactorContext> compileFilter(
      final Filter<?> filter,
      final Object configuration
  ) {
    return (CompiledFilter<ReactorContext>) filter.compile(configuration);
  }

  @SuppressWarnings("unchecked")
  private CompiledCondition<ReactorContext> compileCondition(
      final Condition<?> condition,
      final Map<String, Object> arguments
  ) {
    return (CompiledCondition<ReactorContext>) condition.compile(arguments);
  }

  @SuppressWarnings("unchecked")
  private CompiledEffect<ReactorContext> compileEffect(
      final Effect<?> effect,
      final Map<String, Object> arguments
  ) {
    return (CompiledEffect<ReactorContext>) effect.compile(arguments);
  }

  private void publishRuntime() {
    Map<String, MutableTriggerPlan> grouped = new LinkedHashMap<>();
    for (List<TriggerBinding> bindings : ownerReactions.values()) {
      for (TriggerBinding binding : bindings) {
        MutableTriggerPlan plan = grouped.computeIfAbsent(
            binding.triggerId,
            ignored -> new MutableTriggerPlan(binding.contextType)
        );
        if (plan.contextType != binding.contextType) {
          throw new IllegalStateException(
              "Trigger context changed while reactions were active: " + binding.triggerId
          );
        }
        plan.reactions.add(binding.reaction);
      }
    }
    Map<String, TriggerPlan> updated = new LinkedHashMap<>();
    grouped.forEach((id, plan) -> updated.put(
        id,
        new TriggerPlan(plan.contextType, plan.reactions.toArray(CompiledReaction[]::new))
    ));
    runtime = Map.copyOf(updated);
  }

  private void execute(
      final String triggerId,
      final CompiledReaction reaction,
      final ReactorContext context
  ) {
    try {
      if (!reaction.matches(context)) {
        return;
      }
    } catch (RuntimeException exception) {
      report(triggerId, reaction, "predicate", exception);
      return;
    }
    for (CompiledReaction.NamedEffect effect : reaction.getEffects()) {
      try {
        effect.getEffect().execute(context);
      } catch (RuntimeException exception) {
        report(triggerId, reaction, effect.getId(), exception);
        return;
      }
    }
  }

  private void report(
      final String triggerId,
      final CompiledReaction reaction,
      final String componentId,
      final RuntimeException exception
  ) {
    String key = reaction.getOwner() + ':' + reaction.getId() + ':' + triggerId + ':' + componentId
        + ':' + exception.getClass().getName() + ':' + exception.getMessage();
    long now = System.nanoTime();
    Long previous = reportedErrors.getIfPresent(key).orElse(null);
    if (previous != null && now - previous < ERROR_INTERVAL_NANOS) {
      return;
    }
    reportedErrors.put(key, now);
    LOGGER.log(
        System.Logger.Level.ERROR,
        "Reaction '" + reaction.getOwner() + ':' + reaction.getId() + "' failed for trigger '"
            + triggerId + "' at component '" + componentId + '\'',
        exception
    );
  }

  private static void requireCompatible(
      final String reactionId,
      final Class<? extends ReactorContext> provided,
      final Class<? extends ReactorContext> required,
      final String role,
      final String componentId
  ) {
    if (!required.isAssignableFrom(provided)) {
      throw new IllegalArgumentException(
          "Reaction '" + reactionId + "' uses " + role + " '" + componentId + "' requiring "
              + required.getSimpleName() + ", but its trigger provides " + provided.getSimpleName()
      );
    }
  }

  private static String componentId(final Class<?> componentType) {
    ReactorId annotation = componentType.getAnnotation(ReactorId.class);
    if (annotation == null) {
      throw new IllegalArgumentException(
          "Reaction component is missing @ReactorId: " + componentType.getName()
      );
    }
    return requireId(annotation.value(), "component");
  }

  private static String requireId(final String value, final String role) {
    String id = Objects.requireNonNull(value, role + "Id").trim();
    if (!id.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")) {
      throw new IllegalArgumentException(
          "Invalid " + role + " ID '" + value + "'; use lowercase words separated by hyphens"
      );
    }
    return id;
  }

  private void requireAvailable(final ReactorComponentKind kind, final String id) {
    RegisteredReactorComponent<?> existing = switch (kind) {
      case TRIGGER -> triggers.get(id);
      case FILTER -> filters.get(id);
      case CONDITION -> conditions.get(id);
      case EFFECT -> effects.get(id);
      default -> throw new IllegalStateException("Unsupported reaction component kind: " + kind);
    };
    if (existing != null) {
      throw new IllegalStateException(
          "Reaction component '" + id + "' is already registered by "
              + existing.getOwner().getServiceOwnerName()
      );
    }
  }

  private static <T> void register(
      final Map<String, RegisteredReactorComponent<T>> values,
      final String id,
      final ServiceOwner owner,
      final T component
  ) {
    RegisteredReactorComponent<T> registered = new RegisteredReactorComponent<>(id, owner, component);
    RegisteredReactorComponent<T> existing = values.putIfAbsent(id, registered);
    if (existing != null) {
      throw new IllegalStateException(
          "Reaction component '" + id + "' is already registered by "
              + existing.getOwner().getServiceOwnerName()
      );
    }
  }

  private static <T> RegisteredReactorComponent<T> require(
      final Map<String, RegisteredReactorComponent<T>> values,
      final String id,
      final String role
  ) {
    RegisteredReactorComponent<T> registered = values.get(id);
    if (registered == null) {
      throw new IllegalArgumentException("Unknown " + role + " ID: " + id);
    }
    return registered;
  }

  private static <T> void removeOwned(
      final Map<String, RegisteredReactorComponent<T>> values,
      final ServiceOwner owner
  ) {
    values.values().removeIf(component -> component.getOwner() == owner);
  }

  private record TriggerBinding(
      String triggerId,
      Class<? extends ReactorContext> contextType,
      CompiledReaction reaction
  ) { }

  private record TriggerPlan(
      Class<? extends ReactorContext> contextType,
      CompiledReaction[] reactions
  ) { }

  private static final class MutableTriggerPlan {
    private final Class<? extends ReactorContext> contextType;
    private final List<CompiledReaction> reactions = new ArrayList<>();

    private MutableTriggerPlan(final Class<? extends ReactorContext> contextType) {
      this.contextType = contextType;
    }
  }
}
