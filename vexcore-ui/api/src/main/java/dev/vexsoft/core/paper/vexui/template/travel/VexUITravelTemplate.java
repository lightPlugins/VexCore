package dev.vexsoft.core.paper.vexui.template.travel;

import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.VexUIView;
import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.action.VexUIActions;
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

/** Compact direct-travel workspace with independently paginated categories and aligned destination controls. */
public final class VexUITravelTemplate {

    /** Creates a fresh viewer workspace, optionally opening a known category. */
    public VexUIView createView(final VexUITravelProvider provider, final String category) {
        return new View(Objects.requireNonNull(provider, "provider"), category);
    }

    private static final class View implements VexUIView {

        private final VexUITravelProvider provider;
        private final Map<String, Integer> pages = new HashMap<>();
        private String category;
        private int categoryPage;
        private boolean initialized;

        private View(final VexUITravelProvider provider, final String category) {
            this.provider = provider;
            this.category = category;
        }

        @Override
        public VexUIFrame render(final VexUISession session) {
            var snapshot = session.bind(provider);
            var sidebar = provider.layout("categories");
            if (!snapshot.valid()) {
                session.close();
                return new VexUIFrame(provider.text("title"), provider.text("close"), sidebar.width(),
                    new VexUISpacer(VexUISize.fixed(1, 1)), Map.of());
            }
            var selected = snapshot.categories().stream().filter(value -> value.id().equals(category)).findFirst()
                .orElse(snapshot.categories().isEmpty() ? null : snapshot.categories().getFirst());
            category = selected == null ? null : selected.id();
            if (!initialized && selected != null) {
                categoryPage = snapshot.categories().indexOf(selected) / sidebar.capacity();
                initialized = true;
            }
            var categories = VexUIPagedGrid.slice(snapshot.categories(), categoryPage, sidebar.capacity());
            categoryPage = categories.page();
            var layout = selected == null ? new VexUITemplateLayout(sidebar.width(), 2, 3, 46, 3)
                : provider.layout(selected.id());
            var destinations = VexUIPagedGrid.slice(selected == null ? List.<VexUITravelSnapshot.Destination>of()
                : selected.destinations(), pages.getOrDefault(category, 0), layout.capacity());
            if (category != null) {
                pages.put(category, destinations.page());
            }
            Map<String, VexUIAction> actions = new LinkedHashMap<>();
            var categoryGrid = categories.grid(1, sidebar.gap(), sidebar.cellHeight(), entry -> {
                String id = "category." + entry.id();
                VexUIActions.register(actions, id, ignored -> category = entry.id());
                var caption = new VexUIColumn(1, new VexUIText(VexUISize.flexible(), entry.name(), true),
                    new VexUIText(VexUISize.flexible(), provider.text("discovered", Map.of(
                        "discovered", Integer.toString(entry.discovered()),
                        "total", Integer.toString(entry.destinations().size()))), true));
                return new VexUIButton(new VexUISize(-1, sidebar.cellHeight(), 0), caption,
                    session.styleSheet().normal(), !entry.destinations().isEmpty(), id, entry.tooltip())
                    .withRole(entry.id().equals(category) ? VexUIButtonRole.SELECTED : VexUIButtonRole.NORMAL);
            });
            var targetGrid = destinations.grid(layout.columns(), layout.gap(), layout.cellHeight(), entry -> {
                boolean enabled = entry.enabled() && !snapshot.travelling();
                String id = "travel." + entry.id();
                if (enabled) {
                    VexUIActions.register(actions, id, ignored -> provider.travel(entry.id()));
                }
                String label = !entry.discovered() ? "locked_button" : entry.current() ? "current_button" : "travel";
                var button = new VexUIButton(new VexUISize(54, 22, 0),
                    new VexUIText(VexUISize.flexible(), provider.text(label), true),
                    session.styleSheet().normal(), enabled, id, entry.tooltip()).withRole(VexUIButtonRole.NORMAL);
                var caption = new VexUIColumn(new VexUISize(-1, -1, 1), 1, List.of(
                    new VexUIText(VexUISize.flexible(), entry.name(), false),
                    new VexUIText(VexUISize.flexible(), entry.status(), false)));
                return new VexUIPanel(new VexUISize(-1, layout.cellHeight(), 0), 3,
                    session.styleSheet().normal(), paddedRow(List.of(entry.icon().at(16), caption, button)))
                    .withTooltip(entry.tooltip());
            });
            int categoryHeight = height(sidebar);
            int destinationHeight = height(layout);
            int bodyHeight = Math.max(categoryHeight, destinationHeight + 36);
            List<VexUIElement> left = new ArrayList<>();
            left.add(new VexUIText(provider.text("categories")));
            left.add(categoryGrid);
            pad(left, bodyHeight - categoryHeight);
            List<VexUIElement> right = new ArrayList<>();
            right.add(new VexUIText(selected == null ? provider.text("empty") : selected.name()));
            right.add(targetGrid);
            pad(right, bodyHeight - destinationHeight - 36);
            List<VexUIElement> status = new ArrayList<>();
            status.add(new VexUIParagraph(VexUISize.flexible(), snapshot.journey(), 2));
            if (snapshot.travelling()) {
                if (snapshot.cancellable()) {
                    VexUIActions.register(actions, "travel.cancel", ignored -> provider.cancel());
                }
                status.add(new VexUIButton(new VexUISize(70, 22, 0),
                    new VexUIText(VexUISize.flexible(), provider.text("cancel"), true),
                    session.styleSheet().normal(), snapshot.cancellable(), "travel.cancel", Component.empty()));
            }
            right.add(new VexUIPanel(new VexUISize(-1, 32, 0), 3, session.styleSheet().normal(), paddedRow(status)));
            var back = VexUINavigation.back(session, provider.text("back"), actions);
            var header = new VexUIRow(4, new VexUIText(VexUISize.flexible(), provider.text("subtitle"), false),
                new VexUIButton(new VexUISize(back.size().width(), 22, 0),
                    new VexUIText(VexUISize.flexible(), provider.text("back"), true), back.theme(),
                    back.enabled(), back.action(), back.tooltip()).withRole(back.role()));
            var content = columns(new VexUIColumn(VexUISize.content(), 3, left),
                new VexUIColumn(VexUISize.content(), 3, right));
            var navigation = columns(pager(session, "categories", categories, actions, value -> categoryPage = value),
                pager(session, "destinations", destinations, actions, value -> pages.put(category, value)));
            return new VexUIFrame(provider.text("title"), provider.text("close"), sidebar.width(),
                new VexUIPanel(VexUISize.content(), 6, session.styleSheet().panel(),
                    new VexUIColumn(4, header, content, navigation)), actions);
        }

        @Override
        public void closed() {
            provider.close();
        }

        private VexUIElement pager(
            final VexUISession session, final String prefix, final VexUIPagedGrid<?> current,
            final Map<String, VexUIAction> actions, final IntConsumer changed
        ) {
            VexUIActions.register(actions, prefix + ".previous", ignored -> changed.accept(Math.max(0, current.page() - 1)));
            VexUIActions.register(actions, prefix + ".next", ignored ->
                changed.accept(Math.min(current.pages() - 1, current.page() + 1)));
            return VexUIPagination.compact(current, provider.text("previous"), provider.text("page", Map.of(
                "page", Integer.toString(current.page() + 1), "pages", Integer.toString(current.pages()))),
                provider.text("next"), prefix, session.styleSheet().normal());
        }

        private VexUIGrid columns(final VexUIElement left, final VexUIElement right) {
            return new VexUIGrid(VexUISize.content(), 4, 8,
                List.of(new VexUIGridCell(1, 1, left), new VexUIGridCell(3, 1, right)));
        }

        private VexUIRow paddedRow(final List<VexUIElement> children) {
            // Extra horizontal breathing room preserves the compact cell heights and common pager baseline.
            return new VexUIRow(0, new VexUISpacer(VexUISize.fixed(4, 0)),
                new VexUIRow(VexUISize.flexible(), 3, children), new VexUISpacer(VexUISize.fixed(4, 0)));
        }

        private int height(final VexUITemplateLayout layout) {
            return layout.rows() * layout.cellHeight() + (layout.rows() - 1) * layout.gap();
        }

        private void pad(final List<VexUIElement> target, final int height) {
            if (height > 0) {
                target.add(new VexUISpacer(new VexUISize(-1, Math.max(0, height - 3), 0)));
            }
        }
    }
}
