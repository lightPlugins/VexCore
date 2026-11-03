package dev.vexsoft.core.paper.vexui;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;

/** Reusable search and clear controls backed by the explicit-submit native text form. */
public final class VexUISearchBar {

    private VexUISearchBar() {
    }

    /** Registers search and search.clear actions; all labels and accepted-query behavior belong to the caller. */
    public static VexUIRow controls(
        final String prefix,
        final VexUISession session,
        final VexUISearch form,
        final Component searchLabel,
        final Component clearLabel,
        final boolean hasQuery,
        final Runnable cleared,
        final VexUIStyleSheet style,
        final Map<String, VexUIAction> actions
    ) {
        VexUIActions.register(actions, prefix, ignored -> session.search(form));
        VexUIActions.register(actions, prefix + ".clear", ignored -> cleared.run());
        return new VexUIRow(VexUISize.flexible(), style.gap(), List.of(
            new VexUIButton(new VexUISize(-1, style.buttonHeight(), 1), new VexUIText(searchLabel),
                style.normal(), true, prefix, form.label()).withRole(VexUIButtonRole.NORMAL),
            new VexUIButton(new VexUISize(style.navigationWidth(), style.buttonHeight(), 0), new VexUIText(clearLabel),
                style.normal(), hasQuery, prefix + ".clear", Component.empty()).withRole(VexUIButtonRole.NORMAL)));
    }
}
