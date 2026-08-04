package dev.vexsoft.core.paper.packet.item;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.packets.internal.FakeItemMetaRule;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

@Dependencies
public final class VexFakeItemMetaStoreService implements FakeItemMetaStoreService {

  private final Map<FakeItemRuleKey, FakeItemMetaRule> rules = new LinkedHashMap<>();

  public VexFakeItemMetaStoreService(final VexServiceRegistry services) {
  }

  @Override
  public synchronized void update(
      final ServiceOwner owner,
      final UUID viewerId,
      final NamespacedKey itemIdKey,
      final String itemId,
      final UnaryOperator<FakeItemMetaRule> updater
  ) {
    FakeItemRuleKey key = new FakeItemRuleKey(owner, viewerId, itemIdKey, itemId);
    FakeItemMetaRule current = rules.getOrDefault(key, FakeItemMetaRule.builder().build());
    FakeItemMetaRule updated = updater.apply(current);
    if (isEmpty(updated)) {
      rules.remove(key);
    } else {
      rules.put(key, updated);
    }
  }

  @Override
  public synchronized void clearOwned(final ServiceOwner owner) {
    rules.keySet().removeIf(key -> key.getOwner().equals(owner));
  }

  @Override
  public synchronized void clearOwned(final ServiceOwner owner, final UUID viewerId) {
    rules.keySet().removeIf(key ->
        key.getOwner().equals(owner) && viewerId.equals(key.getViewerId())
    );
  }

  @Override
  public synchronized Optional<FakeItemMetaRule> find(
      final UUID viewerId,
      final ItemStack itemStack
  ) {
    FakeItemMetaRule merged = null;
    for (Map.Entry<FakeItemRuleKey, FakeItemMetaRule> entry : rules.entrySet()) {
      FakeItemRuleKey key = entry.getKey();
      if (key.getViewerId() != null && !key.getViewerId().equals(viewerId)) {
        continue;
      }
      String value = itemStack.getPersistentDataContainer().get(
          key.getItemIdKey(),
          PersistentDataType.STRING
      );
      if (!key.getItemId().equals(value)) {
        continue;
      }
      merged = merge(merged, entry.getValue());
    }
    return Optional.ofNullable(merged);
  }

  @Override
  public synchronized boolean hasAny(final UUID viewerId) {
    return rules.keySet().stream().anyMatch(key ->
        key.getViewerId() == null || key.getViewerId().equals(viewerId)
    );
  }

  private static FakeItemMetaRule merge(
      final FakeItemMetaRule current,
      final FakeItemMetaRule next
  ) {
    FakeItemMetaRule.FakeItemMetaRuleBuilder builder = current == null
        ? FakeItemMetaRule.builder()
        : current.toBuilder();
    if (next.getDisplayName() != null) {
      builder.displayName(next.getDisplayName());
    }
    if (next.getItemModel() != null) {
      builder.itemModel(next.getItemModel());
    }
    if (next.getLore() != null) {
      builder.lore(next.getLore()).loreMode(next.getLoreMode());
    }
    return builder.build();
  }

  private static boolean isEmpty(final FakeItemMetaRule rule) {
    return rule.getDisplayName() == null && rule.getItemModel() == null && rule.getLore() == null;
  }
}
