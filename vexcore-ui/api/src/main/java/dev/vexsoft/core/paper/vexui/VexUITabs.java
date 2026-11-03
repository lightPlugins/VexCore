package dev.vexsoft.core.paper.vexui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
}
