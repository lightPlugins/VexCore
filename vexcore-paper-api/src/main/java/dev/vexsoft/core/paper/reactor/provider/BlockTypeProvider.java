package dev.vexsoft.core.paper.reactor.provider;

import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.block.Block;

/** Compiles block keys belonging to one namespace into optimized matchers. */
public interface BlockTypeProvider {

  /** Returns the namespace handled by this provider. */
  String getNamespace();

  /** Compiles one fully qualified block key during reaction reload. */
  Predicate<Block> compile(Key key);
}
