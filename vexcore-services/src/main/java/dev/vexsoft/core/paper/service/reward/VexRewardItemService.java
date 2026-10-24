package dev.vexsoft.core.paper.service.reward;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.paper.reward.item.RewardItem;
import dev.vexsoft.core.paper.reward.item.RewardItemPresentation;
import dev.vexsoft.core.paper.reward.item.RewardItemProvider;
import dev.vexsoft.core.paper.service.profile.ProfileSessionService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Shared namespace resolver and transactional inventory-to-private-drop delivery. */
@Dependencies({ScheduleService.class, ProfileSessionService.class})
public final class VexRewardItemService implements RewardItemService {

    private final Map<String, RewardItemProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, RewardItemPresentation> presentations = new ConcurrentHashMap<>();
    private final ScheduleService schedules;
    private final ProfileSessionService profiles;

    /** Installs the Vanilla provider and captures the Core scheduler. */
    public VexRewardItemService(final VexServiceRegistry services) {
        schedules = services.require(ScheduleService.class);
        profiles = services.require(ProfileSessionService.class);
        providers.put("minecraft", new VanillaProvider());
    }

    @Override
    public AutoCloseable register(final String namespace, final RewardItemProvider provider) {
        if (!namespace.matches("[a-z0-9._-]+")
            || providers.putIfAbsent(namespace, Objects.requireNonNull(provider, "provider")) != null) {
            throw new IllegalArgumentException("Invalid or duplicate reward item namespace: " + namespace);
        }
        return () -> providers.remove(namespace, provider);
    }

    @Override
    public void validate(final RewardItem item) {
        provider(item).validate(item);
    }

    @Override
    public AutoCloseable registerPresentation(final String namespace, final RewardItemPresentation presentation) {
        if (!namespace.matches("[a-z0-9._-]+")
            || presentations.putIfAbsent(namespace, Objects.requireNonNull(presentation, "presentation")) != null) {
            throw new IllegalArgumentException("Invalid or duplicate reward presentation namespace: " + namespace);
        }
        return () -> presentations.remove(namespace, presentation);
    }

    @Override
    public ItemStack create(final RewardItem item) {
        validate(item);
        if (isVirtual(item)) {
            throw new IllegalArgumentException("Virtual items cannot be created as physical rewards");
        }
        return provider(item).create(item);
    }

    @Override
    public boolean isVirtual(final RewardItem item) {
        validate(item);
        return provider(item).isVirtual(item);
    }

    @Override
    public void deliverVirtual(final VexPlayer player, final RewardItem item, final WholeAmount amount) {
        if (!isVirtual(item) || !amount.isPositive()) {
            throw new IllegalArgumentException("Invalid virtual item delivery");
        }
        provider(item).deposit(player, item, amount);
    }

    @Override
    public Component name(final Player viewer, final RewardItem item) {
        return provider(item).name(viewer, item);
    }

    @Override
    public Component describe(final Player viewer, final RewardItem item, final String amount) {
        var key = Objects.requireNonNull(NamespacedKey.fromString(item.key()));
        var formatting = presentations.get(key.getNamespace());
        Component name = name(viewer, item);
        return formatting == null ? Component.text(amount + "× ").append(name)
            : formatting.describe(viewer, item, amount, name);
    }

    @Override
    public void deliver(final VexPlayer player, final List<ItemStack> items) {
        Player platform = player.requirePlatformPlayer(Player.class);
        for (ItemStack item : items) {
            var leftovers = platform.getInventory().addItem(item.clone());
            leftovers.values().forEach(leftover -> {
                if (profiles.deferDelivery(player, List.of(leftover))) {
                    return;
                }
                var location = platform.getLocation().clone();
                ItemStack overflow = leftover.clone();
                player.afterCommit(() -> location.getWorld().dropItem(location, overflow, dropped -> {
                    // Configure visibility before the entity is announced to any tracking client.
                    dropped.setVisibleByDefault(false);
                    dropped.setOwner(player.getUniqueId());
                    dropped.setCanMobPickup(false);
                    dropped.getPersistentDataContainer().set(PrivateRewardItemListener.OWNER,
                        PersistentDataType.STRING, player.getUniqueId().toString());
                    if (platform.isOnline()) {
                        platform.showEntity(schedules.getOwner(), dropped);
                    }
                }));
            });
        }
    }

    private RewardItemProvider provider(final RewardItem item) {
        String namespace = Objects.requireNonNull(NamespacedKey.fromString(item.key())).getNamespace();
        RewardItemProvider provider = providers.get(namespace);
        if (provider == null) {
            throw new IllegalArgumentException("No reward item provider for " + namespace);
        }
        return provider;
    }

    private static final class VanillaProvider implements RewardItemProvider {

        @Override
        public void validate(final RewardItem item) {
            Material material = Material.matchMaterial(item.key());
            if (material == null || !material.isItem() || material.isAir() || item.upgradeLevel() != 0) {
                throw new IllegalArgumentException("Invalid Vanilla reward item: " + item.key());
            }
        }

        @Override
        public ItemStack create(final RewardItem item) {
            return new ItemStack(Objects.requireNonNull(Material.matchMaterial(item.key())));
        }

        @Override
        public Component name(final Player viewer, final RewardItem item) {
            return Component.translatable(create(item).translationKey());
        }
    }
}
