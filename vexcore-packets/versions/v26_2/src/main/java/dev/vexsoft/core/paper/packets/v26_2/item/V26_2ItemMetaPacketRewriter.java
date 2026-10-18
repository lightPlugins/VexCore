package dev.vexsoft.core.paper.packets.v26_2.item;

import com.mojang.datafixers.util.Pair;
import dev.vexsoft.core.paper.packets.internal.FakeItemMetaLookup;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EquipmentSlot;

public final class V26_2ItemMetaPacketRewriter {

    private final V26_2ItemMetaTransformer transformer = new V26_2ItemMetaTransformer();

    public Object rewrite(final UUID viewerId, final Object packet, final FakeItemMetaLookup lookup) {
        if (!lookup.hasAny(viewerId)) {
            return packet;
        }
        return switch (packet) {
            case ClientboundBundlePacket bundle -> rewritePacket(viewerId, bundle, lookup);
            case ClientboundContainerSetSlotPacket slot -> rewritePacket(viewerId, slot, lookup);
            case ClientboundContainerSetContentPacket content -> rewritePacket(viewerId, content, lookup);
            case ClientboundSetCursorItemPacket cursor -> rewritePacket(viewerId, cursor, lookup);
            case ClientboundSetEntityDataPacket entity -> rewritePacket(viewerId, entity, lookup);
            case ClientboundSetEquipmentPacket equipment -> rewritePacket(viewerId, equipment, lookup);
            default -> packet;
        };
    }

    public Object sanitize(final UUID viewerId, final Object packet, final FakeItemMetaLookup lookup) {
        if (packet instanceof ServerboundSetCreativeModeSlotPacket creativePacket) {
            ItemStack item = transformer.sanitize(viewerId, creativePacket.itemStack(), lookup);

            if (item != creativePacket.itemStack()) {
                return new ServerboundSetCreativeModeSlotPacket(creativePacket.slotNum(), item);
            }
        }

        if (packet instanceof ServerboundContainerClickPacket clickPacket && lookup.hasAny(viewerId)) {
            Int2ObjectMap<HashedStack> changedSlots = new Int2ObjectOpenHashMap<>(clickPacket.changedSlots().size());

            clickPacket.changedSlots()
                .int2ObjectEntrySet()
                .forEach(entry -> changedSlots.put(entry.getIntKey(), wrap(viewerId, entry.getValue(), lookup)));

            return new ServerboundContainerClickPacket(
                clickPacket.containerId(),
                clickPacket.stateId(),
                clickPacket.slotNum(),
                clickPacket.buttonNum(),
                clickPacket.containerInput(),
                changedSlots,
                wrap(viewerId, clickPacket.carriedItem(), lookup)
            );
        }

        return packet;
    }

    private Packet<? super ClientGamePacketListener> rewritePacket(
        final UUID viewerId,
        final Packet<? super ClientGamePacketListener> packet,
        final FakeItemMetaLookup lookup
    ) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            List<Packet<? super ClientGamePacketListener>> children = new ArrayList<>();
            boolean changed = false;
            for (var child : bundle.subPackets()) {
                var rewritten = rewritePacket(viewerId, child, lookup);
                children.add(rewritten);
                changed |= rewritten != child;
            }
            return changed ? new ClientboundBundlePacket(children) : packet;
        }
        if (packet instanceof ClientboundContainerSetSlotPacket slotPacket) {
            ItemStack item = transformer.rewrite(viewerId, slotPacket.getItem(), lookup);

            if (item == slotPacket.getItem()) {
                return packet;
            }

            return new ClientboundContainerSetSlotPacket(
                slotPacket.getContainerId(),
                slotPacket.getStateId(),
                slotPacket.getSlot(),
                item
            );
        }

        if (packet instanceof ClientboundContainerSetContentPacket contentPacket) {
            return rewriteContent(viewerId, contentPacket, lookup);
        }

        if (packet instanceof ClientboundSetCursorItemPacket cursorPacket) {
            ItemStack item = transformer.rewrite(viewerId, cursorPacket.contents(), lookup);

            return item == cursorPacket.contents() ? packet : new ClientboundSetCursorItemPacket(item);
        }

        if (packet instanceof ClientboundSetEntityDataPacket entityPacket) {
            List<SynchedEntityData.DataValue<?>> values = null;
            for (int index = 0; index < entityPacket.packedItems().size(); index++) {
                var value = entityPacket.packedItems().get(index);
                if (value.serializer() == EntityDataSerializers.ITEM_STACK && value.value() instanceof ItemStack item) {
                    ItemStack rewritten = transformer.rewrite(viewerId, item, lookup);
                    if (rewritten != item) {
                        if (values == null) {
                            values = new ArrayList<>(entityPacket.packedItems());
                        }
                        values.set(index, new SynchedEntityData.DataValue<>(value.id(), EntityDataSerializers.ITEM_STACK,
                            rewritten));
                    }
                }
            }
            return values == null ? packet : new ClientboundSetEntityDataPacket(entityPacket.id(), values);
        }

        if (packet instanceof ClientboundSetEquipmentPacket equipmentPacket) {
            List<Pair<EquipmentSlot, ItemStack>> slots = new ArrayList<>(equipmentPacket.getSlots().size());
            boolean changed = false;
            for (var slot : equipmentPacket.getSlots()) {
                ItemStack item = slot.getSecond();
                ItemStack rewritten = transformer.rewrite(viewerId, item, lookup);
                slots.add(Pair.of(slot.getFirst(), rewritten));
                changed |= rewritten != item;
            }
            return changed ? new ClientboundSetEquipmentPacket(equipmentPacket.getEntity(), slots) : packet;
        }

        return packet;
    }

    private HashedStack wrap(final UUID viewerId, final HashedStack stack, final FakeItemMetaLookup lookup) {
        if (stack == HashedStack.EMPTY || stack instanceof SanitizingHashedStack) {
            return stack;
        }

        return new SanitizingHashedStack(viewerId, stack, lookup);
    }

    private ClientboundContainerSetContentPacket rewriteContent(
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

        return new ClientboundContainerSetContentPacket(packet.containerId(), packet.stateId(), rewritten, carried);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private final class SanitizingHashedStack implements HashedStack {

        private final UUID viewerId;
        private final HashedStack delegate;
        private final FakeItemMetaLookup lookup;

        @Override
        public boolean matches(final ItemStack item, final HashedPatchMap.HashGenerator hasher) {
            return delegate.matches(transformer.rewrite(viewerId, item, lookup), hasher);
        }
    }
}
