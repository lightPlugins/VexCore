package dev.vexsoft.core.paper.vexui.component;

import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.action.VexUIActions;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIEntrance;
import dev.vexsoft.core.paper.vexui.element.VexUIHotspot;
import dev.vexsoft.core.paper.vexui.element.VexUIMap;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.element.VexUITexture;
import dev.vexsoft.core.paper.vexui.style.VexUIButtonRole;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
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

    /** Draws the upper-right panorama back arrow and caption over one native click target. */
    public static List<VexUIMap.Layer> panoramaBack(
        final VexUISession session,
        final int sceneWidth,
        final Key controlsFont,
        final String action,
        final Component label
    ) {
        int width = 60;
        int x = sceneWidth - width;
        var caption = IconCaption.centered(session, x, width, 10, 2, label);
        var arrow = new VexUITexture(controlsFont, 0xE004, 10, 12, 11, Component.empty(), VexUIEntrance.SETTLED);
        var textSize = VexUISize.fixed(caption.textWidth(), 9);
        return List.of(
            new VexUIMap.Layer(caption.iconX(), 21, 11, 12, arrow),
            new VexUIMap.Layer(caption.textX(), 21, caption.textWidth(), 9, new VexUIText(textSize, label, false)),
            new VexUIMap.Layer(x, 18, width, 18, new VexUIHotspot(VexUISize.fixed(width, 18), action, label))
        );
    }

    /** Measured positions of an icon and its fitted single-line caption in one control. */
    public record IconCaption(int iconX, int textX, int textWidth) {

        /** Centers the visible ink of the complete icon and caption inside the supplied width. */
        public static IconCaption centered(
            final VexUISession session,
            final int x,
            final int width,
            final int iconWidth,
            final int gap,
            final Component label
        ) {
            int textWidth = session.fittedTextWidth(label, width - iconWidth - gap);
            int spacing = textWidth > 0 ? gap : 0;
            // The last glyph's advance includes one trailing pixel between letters.
            int inkWidth = Math.max(0, textWidth - 1);
            int iconX = x + (width - iconWidth - spacing - inkWidth) / 2;
            return new IconCaption(iconX, iconX + iconWidth + spacing, Math.max(1, textWidth));
        }
    }
}
