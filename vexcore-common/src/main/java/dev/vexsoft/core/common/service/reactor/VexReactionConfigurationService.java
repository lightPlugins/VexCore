package dev.vexsoft.core.common.service.reactor;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.reactor.ReactionConfigurationService;
import dev.vexsoft.core.api.service.reactor.ReactorEngine;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.stats.StatRegistry;
import dev.vexsoft.core.reactor.ReactionComponentDefinition;
import dev.vexsoft.core.reactor.ReactionDefinition;
import dev.vexsoft.core.reactor.ReactionTriggerDefinition;
import dev.vexsoft.core.stats.Stat;
import dev.vexsoft.core.stats.StatDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Strict YAML-facing reaction loader with messages written for server administrators. */
@Dependencies({ReactorEngine.class, StatRegistry.class})
public final class VexReactionConfigurationService implements ReactionConfigurationService {

  private final ReactorEngine reactor;
  private final StatRegistry stats;
  private final String ownerNamespace;

  public VexReactionConfigurationService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    reactor = checkedServices.require(ReactorEngine.class);
    stats = checkedServices.require(StatRegistry.class);
    ownerNamespace = checkedServices.getOwner().getServiceOwnerName()
        .trim()
        .toLowerCase(Locale.ROOT)
        .replace('-', '_');
  }

  @Override
  public List<ReactionDefinition> load(
      final ConfigurationSection configuration,
      final String path
  ) {
    ConfigurationSection checkedConfiguration = Objects.requireNonNull(
        configuration,
        "configuration"
    );
    String checkedPath = requireText(path, "configuration path");
    Object configured = checkedConfiguration.get(checkedPath);
    if (!(configured instanceof List<?> entries)) {
      throw invalid(checkedPath, "must be a list of reaction maps");
    }
    List<ReactionDefinition> definitions = new ArrayList<>(entries.size());
    for (int index = 0; index < entries.size(); index++) {
      String location = checkedPath + '[' + index + ']';
      definitions.add(parseReaction(asMap(entries.get(index), location), checkedPath, location, index));
    }
    return List.copyOf(definitions);
  }

  @Override
  public void reload(final ConfigurationSection configuration, final String path) {
    reactor.reload(load(configuration, path));
  }

  @Override
  public void reload(
      final ConfigurationSection configuration,
      final String path,
      final Collection<StatDefinition> definitions
  ) {
    List<ReactionDefinition> reactions = load(configuration, path);
    Collection<StatDefinition> checkedDefinitions = List.copyOf(
        Objects.requireNonNull(definitions, "stats")
    );
    List<StatDefinition> previous = stats.getRegisteredStats().stream()
        .filter(stat -> stat.getKey().namespace().equals(ownerNamespace))
        .map(Stat::getDefinition)
        .toList();
    stats.synchronize(checkedDefinitions);
    try {
      reactor.reload(reactions);
    } catch (RuntimeException | Error failure) {
      try {
        stats.synchronize(previous);
      } catch (RuntimeException | Error rollbackFailure) {
        failure.addSuppressed(rollbackFailure);
      }
      throw failure;
    }
  }

  private ReactionDefinition parseReaction(
      final Map<String, Object> values,
      final String path,
      final String location,
      final int index
  ) {
    boolean enabled = booleanValue(values.get("enabled"), true, location + ".enabled");
    String reactionId = values.containsKey("reaction-id")
        ? requireText(values.get("reaction-id"), location + ".reaction-id")
        : generatedId(path, index);
    List<ReactionTriggerDefinition> triggers = parseTriggers(values.get("triggers"), location);
    List<ReactionComponentDefinition> conditions = parseComponents(
        values.get("conditions"),
        location + ".conditions"
    );
    List<ReactionComponentDefinition> effects = parseComponents(
        values.get("effects"),
        location + ".effects"
    );
    if (effects.isEmpty()) {
      throw invalid(location + ".effects", "must contain at least one effect");
    }
    return ReactionDefinition.builder()
        .id(reactionId)
        .enabled(enabled)
        .triggers(triggers)
        .conditions(conditions)
        .effects(effects)
        .build();
  }

  private List<ReactionTriggerDefinition> parseTriggers(
      final Object configured,
      final String location
  ) {
    if (!(configured instanceof List<?> entries) || entries.isEmpty()) {
      throw invalid(location + ".triggers", "must be a non-empty list of trigger maps");
    }
    List<ReactionTriggerDefinition> triggers = new ArrayList<>(entries.size());
    for (int index = 0; index < entries.size(); index++) {
      String triggerLocation = location + ".triggers[" + index + ']';
      Map<String, Object> values = asMap(entries.get(index), triggerLocation);
      String id = requireText(values.get("id"), triggerLocation + ".id");
      Map<String, Object> filters = values.containsKey("filters")
          ? asMap(values.get("filters"), triggerLocation + ".filters")
          : Map.of();
      triggers.add(ReactionTriggerDefinition.builder().id(id).filters(filters).build());
    }
    return List.copyOf(triggers);
  }

  private List<ReactionComponentDefinition> parseComponents(
      final Object configured,
      final String location
  ) {
    if (configured == null) {
      return List.of();
    }
    if (!(configured instanceof List<?> entries)) {
      throw invalid(location, "must be a list of component maps");
    }
    List<ReactionComponentDefinition> components = new ArrayList<>(entries.size());
    for (int index = 0; index < entries.size(); index++) {
      String componentLocation = location + '[' + index + ']';
      Map<String, Object> values = asMap(entries.get(index), componentLocation);
      String id = requireText(values.get("id"), componentLocation + ".id");
      Map<String, Object> arguments = values.containsKey("args")
          ? asMap(values.get("args"), componentLocation + ".args")
          : Map.of();
      components.add(ReactionComponentDefinition.builder().id(id).arguments(arguments).build());
    }
    return List.copyOf(components);
  }

  private static Map<String, Object> asMap(final Object value, final String location) {
    if (!(value instanceof Map<?, ?> raw)) {
      throw invalid(location, "must be a map");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    raw.forEach((key, entry) -> {
      if (!(key instanceof String text) || text.isBlank()) {
        throw invalid(location, "contains an invalid key");
      }
      result.put(text, entry);
    });
    return Map.copyOf(result);
  }

  private static boolean booleanValue(
      final Object value,
      final boolean defaultValue,
      final String location
  ) {
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Boolean result) {
      return result;
    }
    throw invalid(location, "must be true or false");
  }

  private static String requireText(final Object value, final String location) {
    if (value instanceof String text && !text.isBlank()) {
      return text.trim();
    }
    throw invalid(location, "must be a non-empty string");
  }

  private static String generatedId(final String location, final int index) {
    String base = location.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-+|-+$)", "");
    if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
      base = "reaction";
    }
    return base + '-' + (index + 1);
  }

  private static IllegalArgumentException invalid(
      final String location,
      final String problem
  ) {
    return new IllegalArgumentException("Reaction setting '" + location + "' " + problem);
  }
}
