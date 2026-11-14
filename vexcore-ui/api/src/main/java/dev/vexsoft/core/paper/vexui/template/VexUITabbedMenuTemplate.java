package dev.vexsoft.core.paper.vexui.template;

import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.component.VexUIMenuHeader;
import dev.vexsoft.core.paper.vexui.component.VexUITabs;
import dev.vexsoft.core.paper.vexui.element.VexUIColumn;
import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import dev.vexsoft.core.paper.vexui.element.VexUIPanel;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.style.VexUITheme;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Shared compact menu frame; caller-owned content, footer, actions, and view state remain untouched. */
public record VexUITabbedMenuTemplate(Layout layout, Style style) {

    public static final int HEADER_HEIGHT = 18;
    public static final int TAB_HEIGHT = 18;
    public static final int PADDING = 4;
    public static final int GAP = 3;
    public static final int RENDER_PADDING = 12;

    /** Requires an explicit layout budget and menu skin. */
    public VexUITabbedMenuTemplate {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(style, "style");
    }

    /** Creates the shared tab row for a page whose surrounding frame also supports custom headings. */
    public VexUIElement tabs(final VexUISession session, final List<VexUITabs.Tab> tabs, final String activeTab) {
        return VexUITabs.attached(tabs, activeTab, layout.width() - PADDING * 2, TAB_HEIGHT, GAP,
            session.styleSheet().normal(), session.styleSheet().panel(), style.tabOutline(), style.activeTabOutline());
    }

    /** Builds outlined tabs with the selected tab open toward the content surface. */
    public VexUIFrame render(
        final VexUISession session,
        final Component title,
        final VexUIMenuHeader header,
        final List<VexUITabs.Tab> tabs,
        final String activeTab,
        final VexUIElement content,
        final VexUIElement footer,
        final Map<String, VexUIAction> actions
    ) {
        return render(session, title, header, tabs(session, tabs, activeTab), content, footer, actions);
    }

    /** Retains a custom second row, for example aligned column headings on a detail page. */
    public VexUIFrame render(
        final VexUISession session,
        final Component title,
        final VexUIMenuHeader header,
        final VexUIElement tabs,
        final VexUIElement content,
        final VexUIElement footer,
        final Map<String, VexUIAction> actions
    ) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(footer, "footer");
        var root = new VexUIPanel(VexUISize.content(), PADDING, session.styleSheet().panel(),
            new VexUIColumn(GAP, header.render(session, HEADER_HEIGHT, style.selectedSection(), actions),
                new VexUIColumn(new VexUISize(-1, TAB_HEIGHT, 0), 0, List.of(tabs)),
                new VexUIColumn(new VexUISize(-1, layout.contentHeight(), 0), 0, List.of(content)),
                new VexUIColumn(new VexUISize(-1, layout.footerHeight(), 0), 0, List.of(footer))));
        return new VexUIFrame(title, header.controls().closeTooltip(), layout.width(), root, actions)
            .withCompactNativeCloseButton();
    }

    /** Explicit content budget including the renderer's reserved native-dialog padding. */
    public record Layout(int width, int contentHeight, int footerHeight, int maximumHeight) {

        /** Rejects dimensions exceeding the declared canvas including reserved renderer padding. */
        public Layout {
            int height = PADDING * 2 + HEADER_HEIGHT + TAB_HEIGHT + contentHeight + footerHeight
                + GAP * 3 + RENDER_PADDING;
            if (width < 100 || width > 960 || contentHeight < 0 || contentHeight > 900
                || footerHeight < 0 || footerHeight > 900
                || maximumHeight > 900 || maximumHeight < height) {
                throw new IllegalArgumentException("Tabbed menu exceeds its layout budget");
            }
        }

        /** Uses the existing 18-pixel footer within the 259-pixel workbench envelope. */
        public static Layout compact(final int width, final int contentHeight) {
            return new Layout(width, contentHeight, 18, 259);
        }
    }

    /** Menu geometry colors are independent of localized text styling. */
    public record Style(VexUITheme selectedSection, int tabOutline, int activeTabOutline) {

        /** Validates the selected-section skin and opaque outline colors. */
        public Style {
            Objects.requireNonNull(selectedSection, "selectedSection");
            if ((tabOutline | activeTabOutline) >>> 24 != 0) {
                throw new IllegalArgumentException("Menu outlines require opaque RGB colors");
            }
        }
    }
}
