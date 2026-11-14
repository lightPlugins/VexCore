package dev.vexsoft.core.paper.vexui.component;

import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.action.VexUIActions;
import dev.vexsoft.core.paper.vexui.action.VexUIClickSound;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIColumn;
import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import dev.vexsoft.core.paper.vexui.element.VexUIRow;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUISpacer;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.style.VexUITheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Shared heading, centered section navigation, and session-owned Back/Close controls. */
public record VexUIMenuHeader(
    VexUIElement heading,
    List<VexUIOption> sections,
    String activeSection,
    Controls controls
) {

    public static final int SECTION_WIDTH = 74;
    public static final int BACK_WIDTH = 66;
    public static final int CLOSE_WIDTH = 18;

    /** Copies section choices; their IDs address actions supplied by the consuming view. */
    public VexUIMenuHeader {
        Objects.requireNonNull(heading, "heading");
        sections = List.copyOf(sections);
        Objects.requireNonNull(controls, "controls");
        if (sections.stream().map(VexUIOption::id).distinct().count() != sections.size()) {
            throw new IllegalArgumentException("Duplicate menu section");
        }
        if (activeSection != null && !sections.isEmpty()
            && sections.stream().noneMatch(section -> section.id().equals(activeSection))) {
            throw new IllegalArgumentException("Active menu section is not present");
        }
    }

    /** Registers navigation and builds a header without changing the consuming view's section actions. */
    public VexUIElement render(
        final VexUISession session,
        final int height,
        final VexUITheme selectedTheme,
        final Map<String, VexUIAction> actions
    ) {
        var style = session.styleSheet();
        VexUIButton back;
        if (controls.backEnabled()) {
            back = VexUINavigation.back(session, controls.backLabel(), actions);
        } else {
            back = new VexUIButton(VexUISize.fixed(BACK_WIDTH, height),
                new VexUIText(controls.backLabel()), style.normal(), false, "navigation.back", Component.empty());
        }
        back = new VexUIButton(VexUISize.fixed(BACK_WIDTH, height),
            new VexUIText(VexUISize.fixed(-1, 8), controls.backLabel(), true), back.theme(), back.enabled(),
            back.action(), back.tooltip(), controls.navigationSound()).withPadding(3);
        if (controls.closeEnabled()) {
            VexUIActions.register(actions, "navigation.close", VexUISession::close);
        }
        var close = new VexUIButton(VexUISize.fixed(CLOSE_WIDTH, height),
            new VexUIText(VexUISize.fixed(-1, 8), controls.closeLabel(), true), style.normal(),
            controls.closeEnabled(), "navigation.close", controls.closeTooltip(), controls.navigationSound())
            .withPadding(3);
        if (sections.isEmpty()) {
            return new VexUIRow(new VexUISize(-1, height, 0), 4, List.of(
                new VexUIRow(new VexUISize(-1, height, 1), 0, List.of(heading)), back, close));
        }
        var switches = new ArrayList<VexUIElement>();
        for (var section : sections) {
            boolean selected = section.id().equals(activeSection);
            switches.add(new VexUIButton(VexUISize.fixed(SECTION_WIDTH, height),
                new VexUIText(VexUISize.fixed(-1, 8), section.label(), true),
                selected ? selectedTheme : style.normal(), section.enabled(), section.id(), section.tooltip(),
                controls.sectionSound()).withPadding(2));
        }
        // Equal side weights keep sections centered when titles or navigation controls change width.
        return new VexUIRow(new VexUISize(-1, height, 0), 4, List.of(
            new VexUIColumn(new VexUISize(-1, height, 1), 0, List.of(heading)),
            new VexUIRow(VexUISize.fixed(sections.size() * SECTION_WIDTH + (sections.size() - 1) * 3, height),
                3, switches),
            new VexUIRow(new VexUISize(-1, height, 1), 4, List.of(
                new VexUISpacer(new VexUISize(-1, 1, 1)), back, close))));
    }

    /** Localized navigation labels and availability; sounds may inherit from the surrounding frame. */
    public record Controls(
        Component backLabel,
        Component closeLabel,
        Component closeTooltip,
        boolean backEnabled,
        boolean closeEnabled,
        VexUIClickSound sectionSound,
        VexUIClickSound navigationSound
    ) {

        /** Validates localized control labels while allowing frame-inherited sounds. */
        public Controls {
            Objects.requireNonNull(backLabel, "backLabel");
            Objects.requireNonNull(closeLabel, "closeLabel");
            Objects.requireNonNull(closeTooltip, "closeTooltip");
        }
    }
}
