package dev.vexsoft.core.paper.reactor.filter;

import dev.vexsoft.core.paper.service.reactor.BlockTypeRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.block.Block;

final class BlockFilterSupport {

  private BlockFilterSupport() {
  }

  static Predicate<Block> compile(
      final BlockTypeRegistry registry,
      final Object configuration
  ) {
    Collection<?> values = configuration instanceof Collection<?> collection
        ? collection : List.of(configuration);
    if (values.isEmpty()) {
      throw new IllegalArgumentException("Block filter requires at least one block type");
    }
    List<Predicate<Block>> predicates = new ArrayList<>(values.size());
    for (Object value : values) {
      if (!(value instanceof String text) || text.isBlank()) {
        throw new IllegalArgumentException("Block filter values must be non-empty strings");
      }
      String key = text.indexOf(':') < 0 ? "minecraft:" + text : text;
      predicates.add(registry.compile(Key.key(key)));
    }
    List<Predicate<Block>> compiled = List.copyOf(predicates);
    return block -> {
      for (Predicate<Block> predicate : compiled) {
        if (predicate.test(block)) {
          return true;
        }
      }
      return false;
    };
  }
}
