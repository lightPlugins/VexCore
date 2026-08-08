package dev.vexsoft.core.paper.reactor.filter;

import dev.vexsoft.core.paper.reactor.context.BlockReactorContext;
import dev.vexsoft.core.paper.reactor.provider.BlockTypeRegistry;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.filter.CompiledFilter;
import dev.vexsoft.core.gameplay.reactor.filter.Filter;
import dev.vexsoft.core.gameplay.reactor.ReactorId;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.block.Block;

@ReactorId("not-blocks")
@Dependencies(BlockTypeRegistry.class)
public final class NotBlocksFilter implements Filter<BlockReactorContext> {

  private final BlockTypeRegistry blocks;

  public NotBlocksFilter(final VexServiceRegistry services) {
    blocks = Objects.requireNonNull(services, "services").require(BlockTypeRegistry.class);
  }

  @Override
  public Class<BlockReactorContext> getContextType() {
    return BlockReactorContext.class;
  }

  @Override
  public CompiledFilter<BlockReactorContext> compile(final Object configuration) {
    Predicate<Block> predicate = BlockFilterSupport.compile(blocks, configuration);
    return context -> !predicate.test(context.getBlock());
  }
}
