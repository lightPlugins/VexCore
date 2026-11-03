package dev.vexsoft.core.paper.vexui.debug;

import dev.vexsoft.core.paper.vexui.VexUIAction;
import dev.vexsoft.core.paper.vexui.VexUIButton;
import dev.vexsoft.core.paper.vexui.VexUIColumn;
import dev.vexsoft.core.paper.vexui.VexUIElement;
import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.VexUIItemIcon;
import dev.vexsoft.core.paper.vexui.VexUIPagedGrid;
import dev.vexsoft.core.paper.vexui.VexUIPagination;
import dev.vexsoft.core.paper.vexui.VexUIPanel;
import dev.vexsoft.core.paper.vexui.VexUIRow;
import dev.vexsoft.core.paper.vexui.VexUISearch;
import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.VexUISize;
import dev.vexsoft.core.paper.vexui.VexUIText;
import dev.vexsoft.core.paper.vexui.VexUITheme;
import dev.vexsoft.core.paper.vexui.VexUIView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;

/** Exercises the public layout API using category tabs, pagination, selection, quantity, filtering and buying. */
public final class VexUIDebugShopView implements VexUIView {

    private final List<VexUIDebugProduct> products;
    private final int width;
    private final int columns;
    private final int pageSize;
    private final int gap;
    private final int cardHeight;
    private final int navigationWidth;
    private final BiFunction<String, Map<String, String>, Component> localize;
    private final ToIntFunction<Material> capacity;
    private final BiConsumer<VexUIDebugProduct, Integer> purchase;
    private String category = "all";
    private String query = "";
    private String selected;
    private int page;
    private int quantity = 1;
    private int sort;
    private boolean stackableOnly;

    public VexUIDebugShopView(final List<VexUIDebugProduct> products, final int width, final int columns,
                              final int pageSize, final int gap, final int cardHeight, final int navigationWidth,
                              final BiFunction<String, Map<String, String>, Component> localize,
                              final ToIntFunction<Material> capacity,
                              final BiConsumer<VexUIDebugProduct, Integer> purchase) {
        this.products = List.copyOf(products);
        this.width = width;
        this.columns = columns;
        this.pageSize = pageSize;
        this.gap = gap;
        this.cardHeight = cardHeight;
        this.navigationWidth = navigationWidth;
        this.localize = localize;
        this.capacity = capacity;
        this.purchase = purchase;
    }

    @Override
    public VexUIFrame render(final VexUISession session) {
        Map<String, VexUIAction> actions = new LinkedHashMap<>();
        var filtered = products.stream().filter(product -> category.equals("all") || category.equals(product.category()))
            .filter(product -> !stackableOnly || product.maximumStackSize() > 1)
            .filter(product -> name(product).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
            .sorted(comparator()).toList();
        var current = VexUIPagedGrid.slice(filtered, page, pageSize);
        page = current.page();
        var product = filtered.stream().filter(value -> value.id().equals(selected)).findFirst()
            .orElse(filtered.isEmpty() ? null : filtered.getFirst());
        selected = product == null ? null : product.id();
        if (product != null) {
            quantity = Math.clamp(quantity, 1, product.maximumStackSize());
        }
        List<VexUIElement> content = new ArrayList<>();
        content.add(categories(actions));
        content.add(toolbar(session, actions));
        content.add(current.grid(columns, gap, cardHeight, entry -> {
            String action = "select." + entry.id();
            actions.put(action, ignored -> {
                selected = entry.id();
                quantity = Math.min(quantity, entry.maximumStackSize());
            });
            return new VexUIButton(new VexUISize(-1, cardHeight, 0),
                new VexUIRow(5, new VexUIItemIcon(entry.sprite(), 16, Component.empty()),
                    new VexUIColumn(VexUISize.flexible(), 0, List.of(
                        new VexUIText(t("items." + entry.id())),
                        new VexUIText(t("price", Map.of("price", entry.price().toString())))))),
                entry.id().equals(selected) ? VexUITheme.SELECTED : VexUITheme.BUTTON,
                true, action, t("select"));
        }));
        actions.put("page.previous", ignored -> page = Math.max(0, page - 1));
        actions.put("page.next", ignored -> page = Math.min(current.pages() - 1, page + 1));
        content.add(VexUIPagination.controls(current, nav("previous"),
            nav("page", Map.of("page", Integer.toString(page + 1), "pages", Integer.toString(current.pages()))),
            nav("next"), navigationWidth));
        if (product != null) {
            content.add(details(product, session, actions));
        }
        content.add(new VexUIText(VexUISize.content(), t(current.entries().isEmpty() ? "empty" : "hint"), true));
        return new VexUIFrame(t("title"), nav("close"), width,
            new VexUIColumn(VexUISize.content(), gap, content), actions);
    }

    private VexUIElement categories(final Map<String, VexUIAction> actions) {
        List<String> values = new ArrayList<>(List.of("all"));
        products.stream().map(VexUIDebugProduct::category).distinct().forEach(values::add);
        List<VexUIElement> tabs = new ArrayList<>();
        for (String value : values) {
            String action = "category." + value;
            actions.put(action, ignored -> {
                category = value;
                page = 0;
            });
            tabs.add(new VexUIButton(VexUISize.flexible(), new VexUIText(t("categories." + value)),
                value.equals(category) ? VexUITheme.SELECTED : VexUITheme.BUTTON, true, action, Component.empty()));
        }
        return new VexUIRow(VexUISize.content(), gap, tabs);
    }

    private VexUIElement toolbar(final VexUISession session, final Map<String, VexUIAction> actions) {
        actions.put("search", ignored -> session.search(new VexUISearch(t("search.title"), t("search.label"),
            nav("apply"), nav("back"), query, value -> {
                query = value.trim();
                page = 0;
            })));
        actions.put("reset", ignored -> {
            query = "";
            page = 0;
        });
        actions.put("sort", ignored -> {
            sort = (sort + 1) % 3;
            page = 0;
        });
        actions.put("filter", ignored -> {
            stackableOnly = !stackableOnly;
            page = 0;
        });
        return new VexUIRow(gap,
            new VexUIButton(VexUISize.flexible(), new VexUIText(t("search.button",
                Map.of("query", query.isEmpty() ? "-" : query))), VexUITheme.BUTTON, true, "search", t("search.label")),
            new VexUIButton(new VexUISize(navigationWidth, -1, 0), new VexUIText(t("reset")),
                VexUITheme.BUTTON, !query.isEmpty(), "reset", Component.empty()),
            new VexUIButton(VexUISize.flexible(), new VexUIText(t(stackableOnly ? "filter.stackable" : "filter.all")),
                VexUITheme.BUTTON, true, "filter", Component.empty()),
            new VexUIButton(VexUISize.flexible(), new VexUIText(t("sort." + sort)),
                VexUITheme.BUTTON, true, "sort", Component.empty()));
    }

    private VexUIElement details(final VexUIDebugProduct product, final VexUISession session,
                                  final Map<String, VexUIAction> actions) {
        int maximum = product.maximumStackSize();
        int available = capacity.applyAsInt(product.material());
        int max = Math.min(maximum, available);
        actions.put("amount.minus16", ignored -> quantity = Math.max(1, quantity - 16));
        actions.put("amount.minus1", ignored -> quantity = Math.max(1, quantity - 1));
        actions.put("amount.plus1", ignored -> quantity = Math.min(maximum, quantity + 1));
        actions.put("amount.plus16", ignored -> quantity = Math.min(maximum, quantity + 16));
        actions.put("amount.max", ignored -> quantity = Math.max(1, max));
        actions.put("buy", ignored -> purchase.accept(product, quantity));
        var amount = new VexUIRow(gap,
            amountButton("minus16", quantity > 1), amountButton("minus1", quantity > 1),
            new VexUIText(new VexUISize(25, 20, 0), t("quantity", Map.of("amount", Integer.toString(quantity))), true),
            amountButton("plus1", quantity < maximum), amountButton("plus16", quantity < maximum),
            amountButton("max", quantity != max && max > 0));
        var buy = new VexUIButton(VexUISize.content(), new VexUIText(t(available < quantity ? "full" : "buy",
            Map.of("total", product.price().multiply(quantity).toString()))), VexUITheme.PRIMARY,
            available >= quantity, "buy", t(available < quantity ? "full" : "free"));
        var info = new VexUIRow(VexUISize.flexible(), 6, List.of(
            new VexUIItemIcon(product.sprite(), 32, t("items." + product.id())),
            new VexUIColumn(VexUISize.flexible(), 0, List.of(new VexUIText(t("items." + product.id())),
                new VexUIText(t("price", Map.of("price", product.price().toString()))), new VexUIText(t("balance"))))));
        var controls = new VexUIColumn(new VexUISize(Math.min(245, width / 2), -1, 0), 3, List.of(amount, buy));
        return new VexUIPanel(VexUISize.content(), 5, VexUITheme.PANEL, new VexUIRow(gap, info, controls));
    }

    private VexUIButton amountButton(final String action, final boolean enabled) {
        return new VexUIButton(VexUISize.flexible(), new VexUIText(t("amount." + action)),
            VexUITheme.BUTTON, enabled, "amount." + action, Component.empty());
    }

    private Comparator<VexUIDebugProduct> comparator() {
        Comparator<VexUIDebugProduct> order = switch (sort) {
            case 1 -> Comparator.comparing(VexUIDebugProduct::price);
            case 2 -> Comparator.comparing(VexUIDebugProduct::price).reversed();
            default -> Comparator.comparing(this::name, String.CASE_INSENSITIVE_ORDER);
        };
        return order.thenComparing(VexUIDebugProduct::id);
    }

    private String name(final VexUIDebugProduct product) {
        return PlainTextComponentSerializer.plainText().serialize(t("items." + product.id()));
    }

    private Component t(final String key) {
        return t(key, Map.of());
    }

    private Component t(final String key, final Map<String, String> replacements) {
        return localize.apply("vexui.debug-shop." + key, replacements);
    }

    private Component nav(final String key) {
        return nav(key, Map.of());
    }

    private Component nav(final String key, final Map<String, String> replacements) {
        return localize.apply("vexui.navigation." + key, replacements);
    }
}
