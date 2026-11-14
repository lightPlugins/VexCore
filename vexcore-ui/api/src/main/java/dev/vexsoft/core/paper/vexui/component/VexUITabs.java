package dev.vexsoft.core.paper.vexui.component;

import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.action.VexUIActions;
import dev.vexsoft.core.paper.vexui.action.VexUIClickSound;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIColumn;
import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import dev.vexsoft.core.paper.vexui.element.VexUIPanel;
import dev.vexsoft.core.paper.vexui.element.VexUIRow;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUISpacer;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.style.VexUIButtonRole;
import dev.vexsoft.core.paper.vexui.style.VexUIStyleSheet;
import dev.vexsoft.core.paper.vexui.style.VexUITheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

/** Reusable equal-width category or navigation tabs with themed selection and localized tooltips. */
public final class VexUITabs {

    private VexUITabs() {
    }

    /** Builds horizontal choices and registers their prefixed actions in the current frame. */
    public static VexUIRow controls(
        final String prefix,
        final List<VexUIOption> options,
        final Set<String> selected,
        final Consumer<String> changed,
        final VexUIStyleSheet style,
        final Map<String, VexUIAction> actions
    ) {
        return new VexUIRow(VexUISize.content(), style.gap(), buttons(prefix, options, selected, changed, style, actions));
    }

    /** Builds rectangular outlined tabs; actions and selected state remain owned by the consuming view. */
    public static VexUIRow attached(
        final List<Tab> tabs,
        final String selected,
        final int width,
        final int height,
        final int gap,
        final VexUITheme normal,
        final VexUITheme surface,
        final int outline,
        final int activeOutline
    ) {
        if (tabs.isEmpty() || height < 10 || gap < 0
            || tabs.stream().map(Tab::id).distinct().count() != tabs.size()
            || tabs.stream().noneMatch(tab -> tab.id().equals(selected))) {
            throw new IllegalArgumentException("Invalid attached menu tabs or selection");
        }
        int available = width - gap * (tabs.size() - 1);
        int fixed = tabs.stream().filter(tab -> tab.width() >= 0).mapToInt(Tab::width).sum();
        int flexible = (int) tabs.stream().filter(tab -> tab.width() < 0).count();
        if (fixed > available || flexible > 0 && available - fixed < flexible * 12) {
            throw new IllegalArgumentException("Menu tabs exceed their available width");
        }
        int remaining = available - fixed;
        int rail = normal.outerBorder();
        var buttons = new ArrayList<VexUIElement>();
        for (int index = 0; index < tabs.size(); index++) {
            var tab = tabs.get(index);
            int cell = tab.width();
            if (cell < 0) {
                cell = remaining / flexible;
                remaining -= cell;
                flexible--;
            } else if (index == tabs.size() - 1 && flexible == 0) {
                cell += remaining;
                remaining = 0;
            }
            // Absorb each old inter-button gap into its slot, retaining the existing content-row width.
            int slot = cell + (index + 1 < tabs.size() ? gap : 0);
            boolean active = tab.id().equals(selected);
            int edge = active ? activeOutline : outline;
            int fill = active ? surface.background() : normal.background();
            var caption = active ? tab.label().decorate(TextDecoration.BOLD) : tab.label();
            var content = new VexUIColumn(0,
                strip(slot, rail, edge),
                new VexUIRow(VexUISize.fixed(slot, height - 2), 0, List.of(
                    tile(2, height - 2, rail), tile(1, height - 2, edge),
                    new VexUIPanel(VexUISize.fixed(slot - 6, height - 2), 0, flat(fill),
                        new VexUIText(VexUISize.fixed(-1, 8), caption, true)),
                    tile(1, height - 2, edge), tile(2, height - 2, rail))),
                strip(slot, rail, active ? surface.background() : edge));
            var theme = active ? new VexUITheme(fill, rail, edge, edge) : normal;
            buttons.add(new VexUIButton(VexUISize.fixed(slot, height), content, theme, tab.enabled(),
                tab.id(), tab.tooltip(), tab.clickSound()).withPadding(0));
        }
        return new VexUIRow(VexUISize.fixed(width, height), 0, buttons);
    }

    private static VexUIElement strip(final int width, final int rail, final int color) {
        return new VexUIRow(VexUISize.fixed(width, 1), 0, List.of(
            tile(2, 1, rail), tile(width - 4, 1, color), tile(2, 1, rail)));
    }

    private static VexUIElement tile(final int width, final int height, final int color) {
        return new VexUIPanel(VexUISize.fixed(width, height), 0, flat(color),
            new VexUISpacer(VexUISize.fixed(0, 0)));
    }

    private static VexUITheme flat(final int color) {
        return new VexUITheme(color, color, color, color);
    }

    static List<VexUIElement> buttons(
        final String prefix,
        final List<VexUIOption> options,
        final Set<String> selected,
        final Consumer<String> changed,
        final VexUIStyleSheet style,
        final Map<String, VexUIAction> actions
    ) {
        List<VexUIElement> buttons = new ArrayList<>();
        for (var option : options) {
            String action = prefix + "." + option.id();
            VexUIActions.register(actions, action, ignored -> {
                if (option.enabled()) {
                    changed.accept(option.id());
                }
            });
            buttons.add(new VexUIButton(new VexUISize(-1, style.buttonHeight(), 1), new VexUIText(option.label()),
                style.normal(), option.enabled(), action, option.tooltip())
                .withRole(selected.contains(option.id()) ? VexUIButtonRole.SELECTED : VexUIButtonRole.NORMAL));
        }
        return List.copyOf(buttons);
    }
    /** One localized tab; -1 distributes remaining width and IDs retain existing frame action names. */
    public record Tab(
        String id,
        Component label,
        int width,
        boolean enabled,
        Component tooltip,
        VexUIClickSound clickSound
    ) {

        /** Validates a unique action identity, preferred width, and localized text. */
        public Tab {
            if (id == null || id.isBlank() || width != -1 && (width < 12 || width > 960)) {
                throw new IllegalArgumentException("Invalid menu tab ID or width");
            }
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(tooltip, "tooltip");
        }

        /** Creates a tab without an additional hover description. */
        public Tab(final String id, final Component label, final int width, final boolean enabled,
                   final VexUIClickSound clickSound) {
            this(id, label, width, enabled, Component.empty(), clickSound);
        }
    }

}
