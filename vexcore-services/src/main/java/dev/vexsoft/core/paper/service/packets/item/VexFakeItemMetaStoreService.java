package dev.vexsoft.core.paper.service.packets.item;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.items.VexItemKeys;
import dev.vexsoft.core.paper.packets.internal.FakeItemMetaRule;
import dev.vexsoft.core.paper.packets.item.FakeItemMetaResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Stores owner-specific item presentation rules and applies matching rules for each viewer. */
@Dependencies
public final class VexFakeItemMetaStoreService implements FakeItemMetaStoreService {

    private final Map<FakeItemRuleKey, FakeItemMetaRule> rules = new LinkedHashMap<>();
    private final Map<ServiceOwner, FakeItemMetaResolver> resolvers = new LinkedHashMap<>();

    public VexFakeItemMetaStoreService(final VexServiceRegistry services) {
    }

    @Override
    public synchronized void setResolver(final ServiceOwner owner, final FakeItemMetaResolver resolver) {
        if (resolver == null) {
            resolvers.remove(owner);
        } else {
            resolvers.put(owner, resolver);
        }
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
        resolvers.remove(owner);
    }

    @Override
    public synchronized void clearOwned(final ServiceOwner owner, final UUID viewerId) {
        rules.keySet().removeIf(key -> key.getOwner().equals(owner) && viewerId.equals(key.getViewerId()));
    }

    @Override
    public Optional<FakeItemMetaRule> find(final UUID viewerId, final ItemStack itemStack) {
        Byte preservePresentation =
            itemStack.getPersistentDataContainer().get(VexItemKeys.PRESERVE_PRESENTATION, PersistentDataType.BYTE);

        if (Byte.valueOf((byte) 1).equals(preservePresentation)) {
            return Optional.empty();
        }

        List<Map.Entry<FakeItemRuleKey, FakeItemMetaRule>> staticRules;
        List<FakeItemMetaResolver> dynamicResolvers;
        synchronized (this) {
            staticRules = new ArrayList<>(rules.entrySet());
            dynamicResolvers = List.copyOf(resolvers.values());
        }

        FakeItemMetaRule merged = null;

        for (Map.Entry<FakeItemRuleKey, FakeItemMetaRule> entry : staticRules) {
            FakeItemRuleKey key = entry.getKey();

            if (key.getViewerId() != null && !key.getViewerId().equals(viewerId)) {
                continue;
            }

            String value = itemStack.getPersistentDataContainer().get(key.getItemIdKey(), PersistentDataType.STRING);

            if (!key.getItemId().equals(value)) {
                continue;
            }

            merged = merge(merged, entry.getValue());
        }

        for (FakeItemMetaResolver resolver : dynamicResolvers) {
            FakeItemMetaRule current = merged;
            merged = resolver.resolve(viewerId, itemStack).map(rule -> merge(current, rule)).orElse(current);
        }

        return Optional.ofNullable(merged);
    }

    @Override
    public synchronized boolean hasAny(final UUID viewerId) {
        return !resolvers.isEmpty()
            || rules.keySet().stream().anyMatch(key -> key.getViewerId() == null || key.getViewerId().equals(viewerId));
    }

    private static FakeItemMetaRule merge(final FakeItemMetaRule current, final FakeItemMetaRule next) {
        FakeItemMetaRule.FakeItemMetaRuleBuilder builder =
            current == null ? FakeItemMetaRule.builder() : current.toBuilder();

        if (next.getDisplayName() != null) {
            builder.displayName(next.getDisplayName());
        }

        if (next.getItemModel() != null) {
            builder.itemModel(next.getItemModel());
        }

        if (next.getPersistentItemModel() != null) {
            builder.persistentItemModel(next.getPersistentItemModel());
        }

        if (next.getTooltipStyle() != null) {
            builder.tooltipStyle(next.getTooltipStyle());
        }

        if (next.isHideVanillaDetails()) {
            builder.hideVanillaDetails(true);
        }

        if (next.getLore() != null) {
            builder.lore(next.getLore()).loreMode(next.getLoreMode());
        }

        return builder.build();
    }

    private static boolean isEmpty(final FakeItemMetaRule rule) {
        return rule.getDisplayName() == null && rule.getItemModel() == null && rule.getTooltipStyle() == null
            && rule.getLore() == null && !rule.isHideVanillaDetails();
    }
}
