package dev.vexsoft.core.paper.reactor.provider;

import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Entity;

/** Compiles entity keys belonging to one namespace into optimized matchers. */
public interface EntityTypeProvider {

  /** Returns the namespace handled by this provider. */
  String getNamespace();

  /** Compiles one fully qualified entity key during reaction reload. */
  Predicate<Entity> compile(Key key);
}
