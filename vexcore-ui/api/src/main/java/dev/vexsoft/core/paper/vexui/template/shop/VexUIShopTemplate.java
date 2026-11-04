package dev.vexsoft.core.paper.vexui.template.shop;

import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.VexUIView;
import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.action.VexUIActionStatus;
import dev.vexsoft.core.paper.vexui.action.VexUIActions;
import dev.vexsoft.core.paper.vexui.action.VexUIAsyncAction;
import dev.vexsoft.core.paper.vexui.component.VexUINavigation;
import dev.vexsoft.core.paper.vexui.component.VexUIPagedGrid;
import dev.vexsoft.core.paper.vexui.component.VexUIQuantitySelector;
import dev.vexsoft.core.paper.vexui.component.VexUISearch;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIColumn;
import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import dev.vexsoft.core.paper.vexui.element.VexUIPanel;
import dev.vexsoft.core.paper.vexui.element.VexUIParagraph;
import dev.vexsoft.core.paper.vexui.element.VexUIRow;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.style.VexUIAttention;
import dev.vexsoft.core.paper.vexui.style.VexUIButtonRole;
import dev.vexsoft.core.paper.vexui.template.VexUIIcon;
import dev.vexsoft.core.paper.vexui.template.internal.VexUITemplateControls;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Catalog shop with categories, search, availability filter, sorting, stable pages, preview, and bounded quantities. */
public final class VexUIShopTemplate {

    /** Creates fresh viewer state; prices and actual purchases are delegated to the adapter. */
    public VexUIView createView(final VexUIShopProvider provider) {
        return new View(Objects.requireNonNull(provider, "provider"));
    }

    /** Immutable catalog presentation; category IDs and product IDs must be unique. */
    public record Snapshot(Component title, Component balance, List<Category> categories, List<Product> products) {
        /** Copies the catalog and checks that every product belongs to a unique declared category. */
        public Snapshot {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(balance, "balance");
            categories = List.copyOf(categories);
            products = List.copyOf(products);
            var categoryIds = categories.stream().map(Category::id).toList();
            if (categoryIds.stream().distinct().count() != categories.size()
                || products.stream().map(Product::id).distinct().count() != products.size()
                || products.stream().anyMatch(product -> !categoryIds.contains(product.category()))) {
                throw new IllegalArgumentException("Invalid shop category or product IDs");
            }
        }
    }

    /** Category tab; the empty category ID is reserved for the template's All filter. */
    public record Category(String id, Component name) {
        /** Validates the category identity and localized label. */
        public Category {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Missing shop category ID");
            }
            Objects.requireNonNull(name, "name");
        }
    }

    /** Presentation only; sortOrder can contain an adapter-defined price or priority rank without exposing an economy. */
    public record Product(
        String id,
        String category,
        Component name,
        Component price,
        List<Component> details,
        VexUIIcon icon,
        int maximumQuantity,
        boolean available,
        int sortOrder,
        VexUIAttention attention
    ) {
        /** Copies tooltip lines and validates the product and quantity bounds. */
        public Product {
            if (id == null || id.isBlank() || category == null || maximumQuantity < 1) {
                throw new IllegalArgumentException("Invalid shop product");
            }
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(attention, "attention");
            details = List.copyOf(details);
        }
    }

    private static final class View implements VexUIView {
        private final VexUIShopProvider provider;
        private final VexUIAsyncAction<Component> purchase = new VexUIAsyncAction<>();
        private String category = "";
        private String query = "";
        private String selected;
        private int page;
        private int categoryPage;
        private int quantity = 1;
        private int sort;
        private boolean availableOnly;
        private Component message = Component.empty();

        private View(final VexUIShopProvider provider) {
            this.provider = provider;
        }

        @Override
        public VexUIFrame render(final VexUISession session) {
            var snapshot = session.bind(provider);
            var layout = provider.layout();
            boolean running = session.bind(purchase.status()) == VexUIActionStatus.RUNNING;
            if (!category.isEmpty() && snapshot.categories().stream().noneMatch(value -> value.id().equals(category))) {
                category = "";
                page = 0;
            }
            Map<String, VexUIAction> actions = new LinkedHashMap<>();
            List<VexUIElement> content = new ArrayList<>();
            categories(session, snapshot, content, actions);
            VexUIActions.register(actions, "search", viewer -> viewer.search(new VexUISearch(provider.text("search_title"),
                provider.text("search_label"), provider.text("apply"), provider.text("back"), query, value -> {
                    query = value.trim();
                    page = 0;
                })));
            VexUIActions.register(actions, "filter", ignored -> {
                availableOnly = !availableOnly;
                page = 0;
            });
            VexUIActions.register(actions, "sort", ignored -> {
                sort = (sort + 1) % 3;
                page = 0;
            });
            content.add(new VexUIRow(4,
                VexUITemplateControls.button(session, provider.text("search"), "search", true),
                VexUITemplateControls.button(session, provider.text(availableOnly ? "available_only" : "all_products"), "filter", true),
                VexUITemplateControls.button(session, provider.text("sort_" + sort), "sort", true)));
            Comparator<Product> order = sort == 1 ? Comparator.comparing(value -> plain(value.name()))
                : sort == 2 ? Comparator.comparingInt(Product::sortOrder) : (first, second) -> 0;
            var filtered = snapshot.products().stream().filter(value -> category.isEmpty() || category.equals(value.category()))
                .filter(value -> !availableOnly || value.available())
                .filter(value -> plain(value.name()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                .sorted(order).toList();
            var current = VexUIPagedGrid.slice(filtered, page, layout.capacity());
            page = current.page();
            content.add(current.grid(layout.columns(), layout.gap(), layout.cellHeight(), value -> {
                String id = "product." + value.id();
                VexUIActions.register(actions, id, ignored -> {
                    selected = value.id();
                    quantity = 1;
                    message = Component.empty();
                });
                var label = new VexUIRow(4, value.icon().at(16), new VexUIColumn(VexUISize.flexible(), 1,
                    List.of(new VexUIText(value.name()), new VexUIText(value.price()))));
                return new VexUIButton(new VexUISize(-1, layout.cellHeight(), 0), label, session.styleSheet().normal(),
                    !running, id, Component.join(JoinConfiguration.newlines(), value.details()))
                    .withRole(value.id().equals(selected) ? VexUIButtonRole.SELECTED : VexUIButtonRole.NORMAL)
                    .withAttention(value.attention());
            }));
            content.add(VexUITemplateControls.page(session, current, provider, actions, value -> page = value));
            if (filtered.isEmpty()) {
                content.add(new VexUIText(provider.text("empty")));
            }
            var product = snapshot.products().stream().filter(value -> value.id().equals(selected)).findFirst().orElse(null);
            if (product != null) {
                selection(session, snapshot, product, running, content, actions);
            } else {
                selected = null;
                content.add(new VexUIText(provider.text("select_product")));
            }
            content.add(new VexUIParagraph(message));
            content.add(VexUINavigation.back(session, provider.text("back"), actions));
            return new VexUIFrame(snapshot.title(), provider.text("close"), layout.width(),
                new VexUIColumn(VexUISize.content(), layout.gap(), content), actions);
        }

        @Override
        public void closed() {
            provider.close();
        }

        private void categories(
            final VexUISession session,
            final Snapshot snapshot,
            final List<VexUIElement> content,
            final Map<String, VexUIAction> actions
        ) {
            var current = VexUIPagedGrid.slice(snapshot.categories(), categoryPage, 8);
            categoryPage = current.page();
            List<VexUIElement> tabs = new ArrayList<>();
            VexUIActions.register(actions, "category.all", ignored -> {
                category = "";
                page = 0;
            });
            tabs.add(VexUITemplateControls.button(session, provider.text("all_categories"), "category.all", true)
                .withRole(category.isEmpty() ? VexUIButtonRole.SELECTED : VexUIButtonRole.NORMAL));
            for (var value : current.entries()) {
                String id = "category." + value.id();
                VexUIActions.register(actions, id, ignored -> {
                    category = value.id();
                    page = 0;
                });
                tabs.add(VexUITemplateControls.button(session, value.name(), id, true)
                    .withRole(category.equals(value.id()) ? VexUIButtonRole.SELECTED : VexUIButtonRole.NORMAL));
            }
            content.add(new VexUIRow(VexUISize.content(), 3, tabs));
            if (current.pages() > 1) {
                VexUIActions.register(actions, "category.previous", ignored -> categoryPage = Math.max(0, categoryPage - 1));
                VexUIActions.register(actions, "category.next", ignored -> categoryPage = Math.min(current.pages() - 1, categoryPage + 1));
                content.add(new VexUIRow(4,
                    VexUITemplateControls.button(session, provider.text("previous"), "category.previous", current.page() > 0),
                    VexUITemplateControls.button(session, provider.text("next"), "category.next", current.page() + 1 < current.pages())));
            }
        }

        private void selection(
            final VexUISession session,
            final Snapshot snapshot,
            final Product product,
            final boolean running,
            final List<VexUIElement> content,
            final Map<String, VexUIAction> actions
        ) {
            quantity = Math.clamp(quantity, 1, product.maximumQuantity());
            int amount = quantity;
            var quote = provider.quote(product.id(), amount);
            var controls = new VexUIQuantitySelector(amount, 1, product.maximumQuantity(), 16).controls("quantity",
                key -> provider.text("quantity_" + key), value -> provider.text("quantity", Map.of("amount", Integer.toString(value))),
                value -> quantity = value, session.styleSheet(), actions);
            VexUIActions.register(actions, "purchase", viewer -> {
                if (product.available() && provider.quote(product.id(), amount).enabled()) {
                    purchase.run(viewer, () -> provider.purchase(product.id(), amount),
                        (active, result) -> message = result, (active, failure) -> message = provider.text("failed"));
                }
            });
            var button = VexUITemplateControls.button(session, running ? provider.text("pending") : quote.button(),
                "purchase", product.available() && quote.enabled() && !running).withRole(VexUIButtonRole.PRIMARY);
            var details = new VexUIColumn(VexUISize.flexible(), 2, List.of(new VexUIText(product.name()),
                new VexUIText(product.price()), new VexUIText(snapshot.balance()), new VexUIParagraph(quote.summary())));
            var purchasePanel = new VexUIColumn(VexUISize.flexible(), 3, List.of(controls, button));
            content.add(new VexUIPanel(VexUISize.content(), session.styleSheet().padding(), session.styleSheet().panel(),
                new VexUIRow(6, product.icon().at(32), details, purchasePanel)));
        }

        private static String plain(final Component value) {
            return PlainTextComponentSerializer.plainText().serialize(value);
        }
    }
}
