package dev.vexsoft.core.paper.reactor.filter;

import dev.vexsoft.core.paper.reactor.context.ItemReactorContext;
import dev.vexsoft.core.paper.service.reactor.ItemTypeRegistry;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.filter.CompiledFilter;
import dev.vexsoft.core.gameplay.reactor.filter.Filter;
import dev.vexsoft.core.gameplay.reactor.ReactorId;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;

@ReactorId("items")
@Dependencies(ItemTypeRegistry.class)
public final class ItemsFilter implements Filter<ItemReactorContext> {
  private final ItemTypeRegistry items;

  public ItemsFilter(final VexServiceRegistry services) {
    items = Objects.requireNonNull(services, "services").require(ItemTypeRegistry.class);
  }

  @Override
  public Class<ItemReactorContext> getContextType() {
    return ItemReactorContext.class;
  }

  @Override
  public CompiledFilter<ItemReactorContext> compile(final Object configuration) {
    Predicate<ItemStack> predicate = TypeFilterSupport.compile(configuration, items::compile,
        "Item");
    return context -> predicate.test(context.getItem());
  }
}
