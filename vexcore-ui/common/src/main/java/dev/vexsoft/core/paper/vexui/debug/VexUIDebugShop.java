package dev.vexsoft.core.paper.vexui.debug;

import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.service.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.paper.service.messages.SendMessageService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.vexui.VexUIDebugOptions;
import dev.vexsoft.core.paper.vexui.VexUIPagination;
import dev.vexsoft.core.paper.vexui.VexUIService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Builds localized acceptance views and grants one stack per validated, free test purchase. */
@Dependencies({VexUIService.class, ConfigurationService.class, LocalizationService.class,
    PlayerService.class, SendMessageService.class, ScheduleService.class})
public final class VexUIDebugShop implements VexUIDebugShopService {

    private final VexUIService ui;
    private final ConfigurationService configurations;
    private final LocalizationService localization;
    private final PlayerService players;
    private final SendMessageService messages;
    private final ScheduleService scheduler;

    public VexUIDebugShop(final VexServiceRegistry services) {
        ui = services.require(VexUIService.class);
        configurations = services.require(ConfigurationService.class);
        localization = services.require(LocalizationService.class);
        players = services.require(PlayerService.class);
        messages = services.require(SendMessageService.class);
        scheduler = services.require(ScheduleService.class);
    }

    @Override
    public void openComponents(final Player player, final boolean inspect) {
        var config = configurations.load("vexui/menus/components.yml");
        var viewer = players.require(player.getUniqueId());
        var view = new VexUIDebugComponentsView(config.getInt("layout.width", 480),
            (key, replacements) -> localization.resolve(
                viewer.getContainer(LanguageContainer.class).getLanguage().getKey(), key, replacements)
                .getComponents().getFirst(),
            () -> {
                var result = new CompletableFuture<Integer>();
                scheduler.runAsyncLater(Duration.ofMillis(500), () -> result.complete(42));
                return result;
            }, inspect);
        var session = ui.open(player, view);
        session.setDebugOptions(inspect ? VexUIDebugOptions.inspect() : VexUIDebugOptions.disabled());
    }

    @Override
    public void open(final Player player) {
        var config = configurations.load("vexui/menus/debug-shop.yml");
        int width = config.getInt("layout.width", 480);
        int columns = config.getInt("layout.columns", 3);
        int pageSize = config.getInt("layout.page-size", 9);
        int gap = config.getInt("layout.gap", 4);
        int cardHeight = config.getInt("layout.card-height", 36);
        if (width < 330 || width > 700 || columns < 1 || columns > 4 || pageSize < 1 || pageSize > 24
            || gap < 2 || gap > 12 || cardHeight < 32 || cardHeight > 60) {
            throw new IllegalArgumentException("Invalid VexUI debug-shop layout");
        }
        List<VexUIDebugProduct> products = new ArrayList<>();
        var categories = config.getSection("categories");
        for (String category : categories.getKeys(false)) {
            for (String name : categories.getStringList(category)) {
                Material material = Material.matchMaterial(name);
                if (material == null || !material.isItem() || material.isAir()) {
                    throw new IllegalArgumentException("Unknown VexUI debug item " + name);
                }
                long price = config.getLong("prices." + name.toLowerCase(Locale.ROOT), 8);
                products.add(new VexUIDebugProduct(material, category, WholeAmount.of(price), material.getMaxStackSize()));
            }
        }
        if (products.isEmpty()) {
            throw new IllegalArgumentException("VexUI debug shop needs at least one product");
        }
        var viewer = players.require(player.getUniqueId());
        ui.open(player, new VexUIDebugShopView(products, width, columns, pageSize, gap, cardHeight,
            VexUIPagination.DEFAULT_BUTTON_WIDTH,
            (key, replacements) -> localization.resolve(
                viewer.getContainer(LanguageContainer.class).getLanguage().getKey(), key, replacements)
                .getComponents().getFirst(),
            material -> capacity(player, material), (product, quantity) -> buy(player, product, quantity)));
    }

    /** Returns storage capacity for an unmodified vanilla stack, excluding armor and offhand slots. */
    public static int capacity(final Player player, final Material material) {
        ItemStack prototype = new ItemStack(material);
        var inventory = player.getInventory();
        int maximum = Math.min(prototype.getMaxStackSize(), inventory.getMaxStackSize());
        var contents = Objects.requireNonNull(inventory.getStorageContents(), "inventory storage contents");
        int capacity = 0;
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir()) {
                capacity += maximum;
            } else if (stack.isSimilar(prototype)) {
                capacity += Math.max(0, maximum - stack.getAmount());
            }
        }
        return capacity;
    }

    private void buy(final Player player, final VexUIDebugProduct product, final int quantity) {
        if (quantity < 1 || quantity > product.material().getMaxStackSize() || capacity(player, product.material()) < quantity) {
            messages.send(player, "vexui.debug-shop.messages.full", true);
            return;
        }
        var inventory = player.getInventory();
        ItemStack[] before = Arrays.stream(inventory.getStorageContents())
            .map(stack -> stack == null ? null : stack.clone()).toArray(ItemStack[]::new);
        try {
            if (!inventory.addItem(new ItemStack(product.material(), quantity)).isEmpty()) {
                inventory.setStorageContents(before);
                messages.send(player, "vexui.debug-shop.messages.full", true);
                return;
            }
        } catch (RuntimeException failure) {
            inventory.setStorageContents(before);
            throw failure;
        }
        messages.send(player, "vexui.debug-shop.messages.purchased", true,
            Map.of("amount", Integer.toString(quantity)));
    }
}
