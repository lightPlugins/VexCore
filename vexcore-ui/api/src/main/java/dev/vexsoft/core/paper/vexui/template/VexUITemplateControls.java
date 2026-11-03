package dev.vexsoft.core.paper.vexui.template;

import dev.vexsoft.core.paper.vexui.VexUIAction;
import dev.vexsoft.core.paper.vexui.VexUIActions;
import dev.vexsoft.core.paper.vexui.VexUIButton;
import dev.vexsoft.core.paper.vexui.VexUIButtonRole;
import dev.vexsoft.core.paper.vexui.VexUIPagedGrid;
import dev.vexsoft.core.paper.vexui.VexUIPagination;
import dev.vexsoft.core.paper.vexui.VexUIRow;
import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.VexUISize;
import dev.vexsoft.core.paper.vexui.VexUIText;
import java.util.Map;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;

/** Shared localized controls for the standard templates. */
final class VexUITemplateControls {

    private VexUITemplateControls() {
    }

    static VexUIButton button(final VexUISession session, final Component label, final String id,
                              final boolean enabled) {
        return new VexUIButton(new VexUISize(-1, session.styleSheet().buttonHeight(), 1), new VexUIText(label),
            session.styleSheet().normal(), enabled, id, Component.empty()).withRole(VexUIButtonRole.NORMAL);
    }

    static VexUIRow page(final VexUISession session, final VexUIPagedGrid<?> page, final VexUITemplateText text,
                         final Map<String, VexUIAction> actions, final IntConsumer changed) {
        VexUIActions.register(actions, "page.previous", ignored -> changed.accept(Math.max(0, page.page() - 1)));
        VexUIActions.register(actions, "page.next", ignored -> changed.accept(Math.min(page.pages() - 1, page.page() + 1)));
        return VexUIPagination.controls(page, text.text("previous"), text.text("page", Map.of(
            "page", Integer.toString(page.page() + 1), "pages", Integer.toString(page.pages()))), text.text("next"),
            session.styleSheet().navigationWidth());
    }
}
