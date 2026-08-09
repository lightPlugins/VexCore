package dev.vexsoft.core.common.service.placeholder;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexClassFactory;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.placeholder.PlaceholderArguments;
import dev.vexsoft.core.placeholder.PlaceholderContext;
import dev.vexsoft.core.placeholder.PlaceholderId;
import dev.vexsoft.core.placeholder.PlaceholderNames;
import dev.vexsoft.core.placeholder.VexPlaceholder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable-snapshot placeholder registry with cached direct-token templates. */
@Dependencies
public final class VexPlaceholderRegistryCoordinatorService
    implements PlaceholderRegistryCoordinatorService {

  private static final Pattern TOKEN = Pattern.compile("%([A-Za-z0-9_]+)%");
  private final Map<String, RegisteredPlaceholder> registrations = new LinkedHashMap<>();
  private final Cache<String, CompiledTemplate> templates = Caffeine.newBuilder()
      .maximumSize(4096)
      .build();
  private volatile List<RegisteredPlaceholder> snapshot = List.of();

  public VexPlaceholderRegistryCoordinatorService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public synchronized <T extends VexPlaceholder> T register(
      final ServiceOwner owner,
      final VexServiceRegistry services,
      final Class<T> placeholderType
  ) {
    ServiceOwner checkedOwner = Objects.requireNonNull(owner, "owner");
    Class<T> checkedType = Objects.requireNonNull(placeholderType, "placeholderType");
    PlaceholderId annotation = checkedType.getAnnotation(PlaceholderId.class);
    if (annotation == null) {
      throw new IllegalArgumentException(
          "Placeholder is missing @PlaceholderId: " + checkedType.getName()
      );
    }
    String namespace = PlaceholderNames.namespace(checkedOwner.getServiceOwnerName());
    String id = PlaceholderNames.identifier(annotation.value());
    String key = namespace + '_' + id;
    if (registrations.containsKey(key)) {
      throw new IllegalStateException("Placeholder %" + key + "% is already registered");
    }
    T placeholder = VexClassFactory.create(checkedType, services, "Placeholder");
    registrations.put(key, new RegisteredPlaceholder(checkedOwner, key, placeholder));
    rebuildSnapshot();
    return placeholder;
  }

  @Override
  public String resolve(final PlaceholderContext context, final String input) {
    PlaceholderContext checkedContext = Objects.requireNonNull(context, "context");
    String checkedInput = Objects.requireNonNull(input, "input");
    if (checkedInput.indexOf('%') < 0) {
      return checkedInput;
    }
    CompiledTemplate template = templates.get(checkedInput, this::compile);
    return template.resolve(checkedContext);
  }

  @Override
  public synchronized void unregisterOwner(final ServiceOwner owner) {
    ServiceOwner checkedOwner = Objects.requireNonNull(owner, "owner");
    if (registrations.values().removeIf(entry -> entry.owner() == checkedOwner)) {
      rebuildSnapshot();
    }
  }

  private CompiledTemplate compile(final String source) {
    Matcher matcher = TOKEN.matcher(source);
    List<TemplatePart> parts = new ArrayList<>();
    int position = 0;
    while (matcher.find()) {
      if (matcher.start() > position) {
        parts.add(new TextPart(source.substring(position, matcher.start())));
      }
      String raw = matcher.group(1);
      ResolvedRegistration resolved = findRegistration(raw.toLowerCase(Locale.ROOT));
      parts.add(new PlaceholderPart(matcher.group(), raw.toLowerCase(Locale.ROOT), resolved));
      position = matcher.end();
    }
    if (position < source.length()) {
      parts.add(new TextPart(source.substring(position)));
    }
    return new CompiledTemplate(parts.toArray(TemplatePart[]::new));
  }

  private ResolvedRegistration findRegistration(final String token) {
    for (RegisteredPlaceholder registration : snapshot) {
      String key = registration.key();
      if (token.equals(key)) {
        return new ResolvedRegistration(registration.placeholder(), PlaceholderArguments.empty());
      }
      if (token.startsWith(key + '_')) {
        String remainder = token.substring(key.length() + 1);
        return new ResolvedRegistration(
            registration.placeholder(),
            PlaceholderArguments.of(List.of(remainder.split("_")))
        );
      }
    }
    return null;
  }

  private synchronized void rebuildSnapshot() {
    snapshot = registrations.values().stream()
        .sorted(Comparator.comparingInt((RegisteredPlaceholder value) -> value.key().length())
            .reversed())
        .toList();
    templates.invalidateAll();
  }

  private interface TemplatePart {
    void append(StringBuilder output, PlaceholderContext context);
  }

  private record TextPart(String text) implements TemplatePart {
    @Override
    public void append(final StringBuilder output, final PlaceholderContext context) {
      output.append(text);
    }
  }

  private record PlaceholderPart(
      String source,
      String name,
      ResolvedRegistration registration
  ) implements TemplatePart {
    @Override
    public void append(final StringBuilder output, final PlaceholderContext context) {
      String local = context.getLocalValue(name);
      if (local != null) {
        output.append(local);
        return;
      }
      if (registration == null) {
        output.append(source);
        return;
      }
      String resolved = registration.placeholder().resolve(
          context.getPlayer(),
          registration.arguments()
      );
      output.append(resolved == null ? source : resolved);
    }
  }

  private record RegisteredPlaceholder(
      ServiceOwner owner,
      String key,
      VexPlaceholder placeholder
  ) { }

  private record ResolvedRegistration(
      VexPlaceholder placeholder,
      PlaceholderArguments arguments
  ) { }

  private record CompiledTemplate(TemplatePart[] parts) {
    String resolve(final PlaceholderContext context) {
      StringBuilder output = new StringBuilder();
      for (TemplatePart part : parts) {
        part.append(output, context);
      }
      return output.toString();
    }
  }
}
