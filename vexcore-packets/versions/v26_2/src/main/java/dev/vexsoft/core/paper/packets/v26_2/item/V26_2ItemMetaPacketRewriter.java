package dev.vexsoft.core.paper.packets.v26_2.item;

import dev.vexsoft.core.paper.packets.internal.FakeItemMetaLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.HashedStack;
import net.minecraft.network.HashedPatchMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.item.ItemStack;

public final class V26_2ItemMetaPacketRewriter {

  private final V26_2ItemMetaTransformer transformer = new V26_2ItemMetaTransformer();

  public Object rewrite(
      final UUID viewerId,
      final Object packet,
      final FakeItemMetaLookup lookup
  ) {
    if (packet instanceof ClientboundContainerSetSlotPacket slotPacket) {
      ItemStack item = transformer.rewrite(viewerId, slotPacket.getItem(), lookup);
      if (item == slotPacket.getItem()) {
        return packet;
      }
      return new ClientboundContainerSetSlotPacket(
          slotPacket.getContainerId(), slotPacket.getStateId(), slotPacket.getSlot(), item
      );
    }
    if (packet instanceof ClientboundContainerSetContentPacket contentPacket) {
      return rewriteContent(viewerId, contentPacket, lookup);
    }
    if (packet instanceof ClientboundSetCursorItemPacket cursorPacket) {
      ItemStack item = transformer.rewrite(viewerId, cursorPacket.contents(), lookup);
      return item == cursorPacket.contents() ? packet : new ClientboundSetCursorItemPacket(item);
    }
    return packet;
  }

  public Object sanitize(
      final UUID viewerId,
      final Object packet,
      final FakeItemMetaLookup lookup
  ) {
    if (packet instanceof ServerboundSetCreativeModeSlotPacket creativePacket) {
      ItemStack item = transformer.sanitize(viewerId, creativePacket.itemStack(), lookup);
      if (item != creativePacket.itemStack()) {
        return new ServerboundSetCreativeModeSlotPacket(creativePacket.slotNum(), item);
      }
    }
    if (packet instanceof ServerboundContainerClickPacket clickPacket && lookup.hasAny(viewerId)) {
      Int2ObjectMap<HashedStack> changedSlots = new Int2ObjectOpenHashMap<>(
          clickPacket.changedSlots().size()
      );
      clickPacket.changedSlots().int2ObjectEntrySet().forEach(entry -> changedSlots.put(
          entry.getIntKey(),
          wrap(viewerId, entry.getValue(), lookup)
      ));
      return new ServerboundContainerClickPacket(
          clickPacket.containerId(), clickPacket.stateId(), clickPacket.slotNum(),
          clickPacket.buttonNum(), clickPacket.containerInput(), changedSlots,
          wrap(viewerId, clickPacket.carriedItem(), lookup)
      );
    }
    return packet;
  }

  private HashedStack wrap(
      final UUID viewerId,
      final HashedStack stack,
      final FakeItemMetaLookup lookup
  ) {
    if (stack == HashedStack.EMPTY || stack instanceof SanitizingHashedStack) {
      return stack;
    }
    return new SanitizingHashedStack(viewerId, stack, lookup);
  }

  private Object rewriteContent(
      final UUID viewerId,
      final ClientboundContainerSetContentPacket packet,
      final FakeItemMetaLookup lookup
  ) {
    List<ItemStack> rewritten = new ArrayList<>(packet.items().size());
    boolean changed = false;
    for (ItemStack item : packet.items()) {
      ItemStack result = transformer.rewrite(viewerId, item, lookup);
      rewritten.add(result);
      changed |= result != item;
    }
    ItemStack carried = transformer.rewrite(viewerId, packet.carriedItem(), lookup);
    changed |= carried != packet.carriedItem();
    if (!changed) {
      return packet;
    }
    return new ClientboundContainerSetContentPacket(
        packet.containerId(), packet.stateId(), rewritten, carried
    );
  }

  private final class SanitizingHashedStack implements HashedStack {

    private final UUID viewerId;
    private final HashedStack delegate;
    private final FakeItemMetaLookup lookup;

    private SanitizingHashedStack(
        final UUID viewerId,
        final HashedStack delegate,
        final FakeItemMetaLookup lookup
    ) {
      this.viewerId = viewerId;
      this.delegate = delegate;
      this.lookup = lookup;
    }

    @Override
    public boolean matches(final ItemStack item, final HashedPatchMap.HashGenerator hasher) {
      return delegate.matches(transformer.rewrite(viewerId, item, lookup), hasher);
    }
  }
}
