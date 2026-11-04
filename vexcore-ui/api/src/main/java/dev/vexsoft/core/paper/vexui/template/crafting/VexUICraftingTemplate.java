package dev.vexsoft.core.paper.vexui.template.crafting;

import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.number.WholeAmountFormatter;
import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.VexUIView;
import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.action.VexUIActionStatus;
import dev.vexsoft.core.paper.vexui.action.VexUIActions;
import dev.vexsoft.core.paper.vexui.action.VexUIAsyncAction;
import dev.vexsoft.core.paper.vexui.component.VexUINavigation;
import dev.vexsoft.core.paper.vexui.component.VexUIPagedGrid;
import dev.vexsoft.core.paper.vexui.component.VexUIPagination;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIColumn;
import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import dev.vexsoft.core.paper.vexui.element.VexUIGrid;
import dev.vexsoft.core.paper.vexui.element.VexUIGridCell;
import dev.vexsoft.core.paper.vexui.element.VexUIPanel;
import dev.vexsoft.core.paper.vexui.element.VexUIParagraph;
import dev.vexsoft.core.paper.vexui.element.VexUIRow;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUISpacer;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.style.VexUIButtonRole;
import dev.vexsoft.core.paper.vexui.template.VexUITemplateLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextDecoration;

/** Shared station workspace with paginated categories, recipes and exact-quantity material details. */
public final class VexUICraftingTemplate {

    /** Creates fresh navigation and quantity state for one station viewer. */
    public VexUIView createView(final VexUICraftingProvider provider) {
        return new View(Objects.requireNonNull(provider, "provider"));
    }

    private static final class View implements VexUIView {

        private final VexUICraftingProvider provider;
        private final Map<String, Integer> pages = new HashMap<>();
        private final VexUIAsyncAction<Component> operation = new VexUIAsyncAction<>();
        private String category;
        private String selected;
        private int categoryPage;
        private int costPage;
        private WholeAmount quantity = WholeAmount.ONE;
        private Component message = Component.empty();

        private View(final VexUICraftingProvider provider) {
            this.provider = provider;
        }

        @Override
        public VexUIFrame render(final VexUISession session) {
            var snapshot = session.bind(provider);
            var sidebar = provider.layout("categories");
            if (!snapshot.valid()) {
                session.close();
                return new VexUIFrame(snapshot.title(), provider.text("close"), sidebar.width(),
                    new VexUISpacer(VexUISize.fixed(1, 1)), Map.of());
            }
            var currentCategory = snapshot.categories().stream().filter(value -> value.id().equals(category)).findFirst()
                .orElse(snapshot.categories().isEmpty() ? null : snapshot.categories().getFirst());
            String resolved = currentCategory == null ? null : currentCategory.id();
            if (!Objects.equals(category, resolved)) {
                selectCategory(resolved);
            }
            var ids = currentCategory == null ? List.<String>of() : currentCategory.recipes();
            if (selected != null && !ids.contains(selected)) {
                selected = null;
                message = Component.empty();
            }
            boolean running = session.bind(operation.status()) == VexUIActionStatus.RUNNING;
            var categories = VexUIPagedGrid.slice(snapshot.categories(), categoryPage, sidebar.capacity());
            categoryPage = categories.page();
            var recipesLayout = provider.layout("recipes");
            var configuredCosts = provider.layout("costs");
            // Published layouts may still contain the former 32-pixel cards; material lines always stay compact.
            var costsLayout = new VexUITemplateLayout(configuredCosts.width(), configuredCosts.columns(),
                configuredCosts.rows(), 16, Math.min(configuredCosts.gap(), 1));
            var recipes = VexUIPagedGrid.slice(ids, pages.getOrDefault(category, 0), recipesLayout.capacity());
            if (category != null) {
                pages.put(category, recipes.page());
            }
            Map<String, VexUIAction> actions = new LinkedHashMap<>();
            var categoryGrid = categories.grid(1, sidebar.gap(), sidebar.cellHeight(), entry -> {
                String id = "category." + entry.id();
                VexUIActions.register(actions, id, ignored -> selectCategory(entry.id()));
                return new VexUIButton(new VexUISize(-1, sidebar.cellHeight(), 0), new VexUIColumn(1,
                    new VexUIText(VexUISize.flexible(), entry.name(), true),
                    new VexUIText(VexUISize.flexible(), provider.text("recipes", Map.of(
                        "amount", Integer.toString(entry.recipes().size()))), true)), session.styleSheet().normal(),
                    !running, id, entry.tooltip()).withRole(entry.id().equals(category)
                        ? VexUIButtonRole.SELECTED : VexUIButtonRole.NORMAL);
            });
            int detailHeight = 44 + Math.max(158, height(costsLayout) + 23);
            int bodyHeight = Math.max(Math.max(height(sidebar), height(recipesLayout)) + 15, detailHeight);
            List<VexUIElement> left = new ArrayList<>();
            left.add(new VexUIText(provider.text("categories")));
            left.add(categoryGrid);
            pad(left, bodyHeight - height(sidebar) - 15);
            List<VexUIElement> right = new ArrayList<>();
            VexUIElement rightPager;
            if (selected == null) {
                right.add(new VexUIRow(4, new VexUIText(VexUISize.flexible(),
                    currentCategory == null ? provider.text("empty") : currentCategory.name(), false),
                    new VexUIText(provider.text("recipes", Map.of("amount", Integer.toString(ids.size()))))));
                var recipeGrid = recipes.grid(recipesLayout.columns(), recipesLayout.gap(), recipesLayout.cellHeight(), id -> {
                    var recipe = provider.recipe(id);
                    String action = "recipe." + id;
                    VexUIActions.register(actions, action, ignored -> {
                        selected = id;
                        quantity = WholeAmount.ONE;
                        costPage = 0;
                        message = Component.empty();
                    });
                    var label = new VexUIRow(5, recipe.icon().at(16), new VexUIColumn(VexUISize.flexible(), 2, List.of(
                        new VexUIText(recipe.name()), new VexUIParagraph(VexUISize.flexible(), recipe.status(), 2))));
                    return new VexUIButton(new VexUISize(-1, recipesLayout.cellHeight(), 0), padded(label),
                        session.styleSheet().normal(), !running, action, recipe.tooltip()).withRole(VexUIButtonRole.NORMAL);
                });
                right.add(ids.isEmpty() ? new VexUIPanel(new VexUISize(-1, height(recipesLayout), 0), 4,
                    session.styleSheet().normal(), new VexUIText(VexUISize.flexible(), provider.text("empty"), true))
                    : recipeGrid);
                pad(right, bodyHeight - height(recipesLayout) - 15);
                rightPager = pager(session, "recipes", recipes, actions, value -> pages.put(category, value));
            } else {
                var quote = provider.quote(selected, quantity);
                var costs = VexUIPagedGrid.slice(quote.costs(), costPage, costsLayout.capacity());
                costPage = costs.page();
                right.add(details(session, quote, costs, costsLayout, running, actions));
                pad(right, bodyHeight - detailHeight);
                rightPager = pager(session, "costs", costs, actions, value -> costPage = value);
            }
            VexUIButton back;
            if (selected == null) {
                back = VexUINavigation.back(session, provider.text("back"), actions);
            } else {
                VexUIActions.register(actions, "back", ignored -> {
                    selected = null;
                    message = Component.empty();
                });
                back = button(session, provider.text("back"), "back", !running);
            }
            Component subtitle = selected == null ? provider.text("subtitle") : provider.text("recipe_subtitle")
                .replaceText(builder -> builder.matchLiteral("%category%").replacement(currentCategory.name()));
            var header = new VexUIRow(4, new VexUIColumn(VexUISize.flexible(), 2, List.of(
                new VexUIText(snapshot.title()), new VexUIText(subtitle))),
                new VexUIButton(new VexUISize(64, 22, 0),
                    new VexUIText(VexUISize.flexible(), provider.text("back"), true), back.theme(),
                    !running && back.enabled(), back.action(), back.tooltip()).withRole(back.role()));
            var content = columns(new VexUIColumn(VexUISize.content(), 3, left),
                new VexUIColumn(VexUISize.content(), 3, right));
            var navigation = columns(pager(session, "categories", categories, actions, value -> categoryPage = value),
                rightPager);
            return new VexUIFrame(snapshot.title(), provider.text("close"), sidebar.width(),
                new VexUIPanel(VexUISize.content(), 6, session.styleSheet().panel(),
                    new VexUIColumn(4, header, content, navigation)), actions);
        }

        @Override
        public void closed() {
            provider.close();
        }

        private VexUIElement details(
            final VexUISession session, final VexUICraftingQuote quote,
            final VexUIPagedGrid<VexUICraftingQuote.Cost> costs, final VexUITemplateLayout layout,
            final boolean running, final Map<String, VexUIAction> actions
        ) {
            var recipe = provider.recipe(selected);
            var result = new VexUIPanel(new VexUISize(-1, 40, 0), 4, session.styleSheet().normal(),
                padded(new VexUIRow(5, recipe.icon().at(32), new VexUIColumn(VexUISize.flexible(), 2, List.of(
                    new VexUIText(recipe.name()), new VexUIParagraph(VexUISize.flexible(),
                        message.equals(Component.empty()) ? provider.text("yield", Map.of(
                            "amount", WholeAmountFormatter.format(quote.resultAmount().divide(quote.executions()))))
                            : message, 1))))))
                .withTooltip(message.equals(Component.empty()) ? recipe.tooltip()
                    : recipe.tooltip().append(Component.newline()).append(message));
            var costGrid = costs.grid(layout.columns(), layout.gap(), layout.cellHeight(), entry -> {
                Component amount = provider.text(entry.fulfilled() ? "cost_available" : "cost_missing", Map.of(
                    "required", WholeAmountFormatter.format(entry.required()),
                    "available", WholeAmountFormatter.format(entry.available())));
                VexUIElement row = new VexUIRow(new VexUISize(-1, layout.cellHeight(), 0), 4, List.of(entry.icon().at(16),
                    new VexUIText(VexUISize.flexible(), (provider.craftable(entry)
                        ? entry.name().decorate(TextDecoration.UNDERLINED) : entry.name()).hoverEvent(entry.tooltip()), false),
                    new VexUIText(amount.hoverEvent(entry.tooltip()))));
                if (provider.craftable(entry)) {
                    String action = "ingredient." + actions.size();
                    String parent = selected;
                    VexUIActions.register(actions, action, active -> provider.openIngredient(active, parent, entry));
                    row = new VexUIButton(new VexUISize(-1, layout.cellHeight(), 0), row,
                        session.styleSheet().normal(), !running, action, entry.tooltip()).withPadding(0);
                }
                return row;
            });
            int height = Math.max(158, height(layout) + 23);
            var materials = new VexUIPanel(new VexUISize(-1, height, 0), 4, session.styleSheet().normal(),
                padded(new VexUIColumn(new VexUISize(-1, height - 8, 0), 3, List.of(new VexUIRow(3,
                    new VexUIText(VexUISize.flexible(), provider.text("costs"), false),
                    new VexUIText(provider.text("cost_columns"))), costGrid))));
            List<VexUIElement> controls = new ArrayList<>();
            controls.add(new VexUIText(provider.text("quantity_title")));
            controls.add(quantityRow(List.of(quantity(session, actions, "minus", -1, running),
                new VexUIPanel(new VexUISize(-1, 22, 0), 3, session.styleSheet().selected(),
                    new VexUIText(VexUISize.flexible(), provider.text("quantity", Map.of(
                        "amount", WholeAmountFormatter.format(quantity))), true)),
                quantity(session, actions, "plus", 1, running))));
            VexUIActions.register(actions, "quantity.maximum", ignored -> {
                var maximum = provider.maximum(selected);
                quantity = maximum.isPositive() ? maximum : WholeAmount.ONE;
                message = Component.empty();
            });
            controls.add(quantityRow(List.of(quantity(session, actions, "minus_many", -16, running),
                button(session, provider.text("maximum"), "quantity.maximum", !running),
                quantity(session, actions, "plus_many", 16, running))));
            controls.add(new VexUIParagraph(VexUISize.flexible(), provider.text("result", Map.of(
                "amount", WholeAmountFormatter.format(quote.resultAmount()))), 1));
            controls.add(new VexUIParagraph(VexUISize.flexible(),
                Component.join(JoinConfiguration.newlines(), quote.requirements()), 3));
            String recipeId = selected;
            if (quote.enabled() && !running) {
                VexUIActions.register(actions, "craft", viewer -> operation.run(viewer,
                    () -> provider.craft(recipeId, quote), (active, feedback) -> message = feedback,
                    (active, failure) -> message = provider.text("failed")));
            }
            var craftButton = button(session, running ? provider.text("pending") : quote.button(), "craft",
                quote.enabled() && !running).withSize(new VexUISize(-1, 26, 0)).withRole(VexUIButtonRole.PRIMARY)
                .withTooltip(Component.join(JoinConfiguration.newlines(), quote.requirements()));
            // Reserve the upper control area so the primary action stays at the bottom for every requirement count.
            var controlArea = new VexUIColumn(new VexUISize(-1, height - 37, 0), 3, controls);
            var quantities = new VexUIPanel(new VexUISize(-1, height, 0), 4, session.styleSheet().normal(),
                padded(new VexUIColumn(3, controlArea, craftButton)));
            var workspace = new VexUIGrid(VexUISize.content(), 11, 4, List.of(
                new VexUIGridCell(6, 1, materials), new VexUIGridCell(5, 1, quantities)));
            return new VexUIColumn(4, result, workspace);
        }

        private VexUIButton quantity(
            final VexUISession session, final Map<String, VexUIAction> actions,
            final String key, final int delta, final boolean running
        ) {
            String id = "quantity." + key;
            VexUIActions.register(actions, id, ignored -> {
                quantity = delta > 0 ? quantity.add(WholeAmount.of(delta))
                    : quantity.compareTo(WholeAmount.of(-delta)) <= 0 ? WholeAmount.ONE
                        : quantity.subtract(WholeAmount.of(-delta));
                message = Component.empty();
            });
            return button(session, provider.text(key), id, !running && (delta > 0 || quantity.compareTo(WholeAmount.ONE) > 0));
        }

        private VexUIElement quantityRow(final List<VexUIElement> entries) {
            return new VexUIGrid(new VexUISize(-1, 22, 0), 3, 2,
                entries.stream().<VexUIElement>map(entry -> new VexUIGridCell(1, 1, entry)).toList());
        }

        private VexUIButton button(
            final VexUISession session, final Component label, final String id, final boolean enabled
        ) {
            return new VexUIButton(new VexUISize(-1, 22, 0),
                new VexUIText(VexUISize.flexible(), label, true), session.styleSheet().normal(), enabled, id,
                Component.empty()).withRole(VexUIButtonRole.NORMAL);
        }

        private VexUIElement pager(
            final VexUISession session, final String prefix, final VexUIPagedGrid<?> page,
            final Map<String, VexUIAction> actions, final IntConsumer changed
        ) {
            VexUIActions.register(actions, prefix + ".previous", ignored -> changed.accept(Math.max(0, page.page() - 1)));
            VexUIActions.register(actions, prefix + ".next", ignored ->
                changed.accept(Math.min(page.pages() - 1, page.page() + 1)));
            String label = prefix.equals("categories") ? "page" : prefix.equals("recipes") ? "recipe_page" : "cost_page";
            return VexUIPagination.compact(page, provider.text("previous"), provider.text(label, Map.of(
                "page", Integer.toString(page.page() + 1), "pages", Integer.toString(page.pages()))),
                provider.text("next"), prefix, session.styleSheet().normal());
        }

        private VexUIGrid columns(final VexUIElement left, final VexUIElement right) {
            return new VexUIGrid(VexUISize.content(), 5, 8,
                List.of(new VexUIGridCell(1, 1, left), new VexUIGridCell(4, 1, right)));
        }

        private VexUIRow padded(final VexUIElement child) {
            return new VexUIRow(0, new VexUISpacer(VexUISize.fixed(4, 0)),
                new VexUIColumn(VexUISize.flexible(), 0, List.of(child)), new VexUISpacer(VexUISize.fixed(4, 0)));
        }

        private void selectCategory(final String id) {
            category = id;
            selected = null;
            message = Component.empty();
        }

        private int height(final VexUITemplateLayout layout) {
            return layout.rows() * layout.cellHeight() + (layout.rows() - 1) * layout.gap();
        }

        private void pad(final List<VexUIElement> entries, final int height) {
            if (height > 0) {
                entries.add(new VexUISpacer(new VexUISize(-1, Math.max(0, height - 3), 0)));
            }
        }
    }
}
