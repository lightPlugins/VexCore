package dev.vexsoft.core.paper.vexui.component;

import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.action.VexUIActions;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.style.VexUIButtonRole;
import java.util.Map;
import net.kyori.adventure.text.Component;

/** Shared Back control preserving the prior view instance and its selections. */
public final class VexUINavigation {

    private VexUINavigation() {
    }

    /** Registers navigation.back, returning to the previous view or closing a root view. */
    public static VexUIButton back(
        final VexUISession session,
        final Component label,
        final Map<String, VexUIAction> actions
    ) {
        VexUIActions.register(actions, "navigation.back", VexUISession::back);
        return new VexUIButton(new VexUISize(session.styleSheet().navigationWidth(),
            session.styleSheet().buttonHeight(), 0), new VexUIText(label), session.styleSheet().normal(), true,
            "navigation.back", Component.empty()).withRole(VexUIButtonRole.NORMAL);
    }
}
