package dev.vexsoft.core.packets.v26_2.item;

import dev.vexsoft.core.packets.internal.FakeItemMetaLookup;
import dev.vexsoft.core.packets.internal.FakeItemMetaRule;
import io.papermc.paper.adventure.PaperAdventure;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

public final class V26_2ItemMetaTransformer {

  public ItemStack rewrite(
      final UUID viewerId,
      final ItemStack item,
      final FakeItemMetaLookup lookup
  ) {
    Optional<FakeItemMetaRule> result = find(viewerId, item, lookup);
    if (result.isEmpty()) {
      return item;
    }
    ItemStack copy = item.copy();
    apply(copy, result.get());
    return copy;
  }

  public ItemStack sanitize(
      final UUID viewerId,
      final ItemStack item,
      final FakeItemMetaLookup lookup
  ) {
    Optional<FakeItemMetaRule> result = find(viewerId, item, lookup);
    if (result.isEmpty()) {
      return item;
    }
    ItemStack copy = item.copy();
    FakeItemMetaRule rule = result.get();
    if (rule.getDisplayName() != null) {
      copy.remove(DataComponents.CUSTOM_NAME);
    }
    if (rule.getItemModel() != null) {
      copy.remove(DataComponents.ITEM_MODEL);
    }
    if (rule.getLore() != null) {
      copy.remove(DataComponents.LORE);
    }
    return copy;
  }

  private static Optional<FakeItemMetaRule> find(
      final UUID viewerId,
      final ItemStack item,
      final FakeItemMetaLookup lookup
  ) {
    if (item == null || item.isEmpty() || !lookup.hasAny(viewerId)) {
      return Optional.empty();
    }
    return lookup.find(viewerId, CraftItemStack.asBukkitCopy(item));
  }

  private static void apply(final ItemStack item, final FakeItemMetaRule rule) {
    if (rule.getDisplayName() != null) {
      item.set(DataComponents.CUSTOM_NAME, PaperAdventure.asVanilla(rule.getDisplayName()));
    }
    if (rule.getItemModel() != null) {
      item.set(DataComponents.ITEM_MODEL, Identifier.parse(rule.getItemModel().asString()));
    }
    if (rule.getLore() == null) {
      return;
    }
    List<net.minecraft.network.chat.Component> fakeLore = rule.getLore().stream()
        .map(PaperAdventure::asVanilla)
        .toList();
    List<net.minecraft.network.chat.Component> lore = switch (rule.getLoreMode()) {
      case REPLACE -> fakeLore;
      case PREPEND -> combine(fakeLore, existingLore(item));
      case APPEND -> combine(existingLore(item), fakeLore);
    };
    int size = Math.min(lore.size(), ItemLore.MAX_LINES);
    item.set(DataComponents.LORE, new ItemLore(List.copyOf(lore.subList(0, size))));
  }

  private static List<net.minecraft.network.chat.Component> existingLore(final ItemStack item) {
    ItemLore lore = item.get(DataComponents.LORE);
    return lore == null ? List.of() : lore.lines();
  }

  private static List<net.minecraft.network.chat.Component> combine(
      final List<net.minecraft.network.chat.Component> first,
      final List<net.minecraft.network.chat.Component> second
  ) {
    List<net.minecraft.network.chat.Component> combined =
        new ArrayList<>(first.size() + second.size());
    combined.addAll(first);
    combined.addAll(second);
    return combined;
  }
}
