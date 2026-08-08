package dev.vexsoft.core.paper.reactor.provider;

import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;

/** Compiles item keys belonging to one namespace into optimized matchers. */
public interface ItemTypeProvider {

  /** Returns the namespace handled by this provider. */
  String getNamespace();

  /** Compiles one fully qualified item key during reaction reload. */
  Predicate<ItemStack> compile(Key key);
}
