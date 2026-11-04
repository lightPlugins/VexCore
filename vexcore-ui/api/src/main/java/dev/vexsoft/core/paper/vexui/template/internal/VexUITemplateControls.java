package dev.vexsoft.core.paper.vexui.template.internal;

import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.action.VexUIActions;
import dev.vexsoft.core.paper.vexui.component.VexUIPagedGrid;
import dev.vexsoft.core.paper.vexui.component.VexUIPagination;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIRow;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.style.VexUIButtonRole;
import dev.vexsoft.core.paper.vexui.template.VexUITemplateText;
import java.util.Map;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;

/** Shared localized controls for the standard templates. */
public final class VexUITemplateControls {

    private VexUITemplateControls() {
    }

    /** Builds a standard button using the viewer's current stylesheet. */
    public static VexUIButton button(final VexUISession session, final Component label, final String id,
                              final boolean enabled) {
        return new VexUIButton(new VexUISize(-1, session.styleSheet().buttonHeight(), 1), new VexUIText(label),
            session.styleSheet().normal(), enabled, id, Component.empty()).withRole(VexUIButtonRole.NORMAL);
    }

    /** Registers page actions and builds their shared localized navigation controls. */
    public static VexUIRow page(final VexUISession session, final VexUIPagedGrid<?> page, final VexUITemplateText text,
                         final Map<String, VexUIAction> actions, final IntConsumer changed) {
        VexUIActions.register(actions, "page.previous", ignored -> changed.accept(Math.max(0, page.page() - 1)));
        VexUIActions.register(actions, "page.next", ignored -> changed.accept(Math.min(page.pages() - 1, page.page() + 1)));
        return VexUIPagination.controls(page, text.text("previous"), text.text("page", Map.of(
            "page", Integer.toString(page.page() + 1), "pages", Integer.toString(page.pages()))), text.text("next"),
            session.styleSheet().navigationWidth());
    }
}
