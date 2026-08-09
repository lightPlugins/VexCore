package dev.vexsoft.core.paper.reactor.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;

final class TypeFilterSupport {

  private TypeFilterSupport() {
  }

  static <T> Predicate<T> compile(
      final Object configuration,
      final Function<Key, Predicate<T>> compiler,
      final String role
  ) {
    Collection<?> values = configuration instanceof Collection<?> collection
        ? collection : List.of(configuration);
    if (values.isEmpty()) {
      throw new IllegalArgumentException(role + " filter requires at least one type");
    }
    List<Predicate<T>> predicates = new ArrayList<>(values.size());
    for (Object value : values) {
      if (!(value instanceof String text) || text.isBlank()) {
        throw new IllegalArgumentException(role + " filter values must be non-empty strings");
      }
      String key = text.indexOf(':') < 0 ? "minecraft:" + text : text;
      predicates.add(compiler.apply(Key.key(key)));
    }
    List<Predicate<T>> compiled = List.copyOf(predicates);
    return candidate -> {
      for (Predicate<T> predicate : compiled) {
        if (predicate.test(candidate)) {
          return true;
        }
      }
      return false;
    };
  }
}
