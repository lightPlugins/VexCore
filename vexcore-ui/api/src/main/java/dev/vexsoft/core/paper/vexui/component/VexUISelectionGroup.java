package dev.vexsoft.core.paper.vexui.component;

import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.element.VexUIGrid;
import dev.vexsoft.core.paper.vexui.element.VexUIRow;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.state.VexUIObservable;
import dev.vexsoft.core.paper.vexui.state.VexUIState;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Viewer-owned single or multiple selection; snapshots integrate with automatic session bindings. */
public final class VexUISelectionGroup {

    private final List<VexUIOption> options;
    private final boolean multiple;
    private final VexUIState<Set<String>> selected;

    /** Creates a validated group; initial selections may include disabled options but not unknown identifiers. */
    public VexUISelectionGroup(final List<VexUIOption> options, final boolean multiple, final Set<String> initial) {
        this.options = List.copyOf(options);
        this.multiple = multiple;
        Set<String> ids = new HashSet<>();
        for (var option : options) {
            if (!ids.add(option.id())) {
                throw new IllegalArgumentException("Duplicate option " + option.id());
            }
        }
        if (!ids.containsAll(initial) || !multiple && initial.size() > 1) {
            throw new IllegalArgumentException("Invalid initial selection");
        }
        selected = new VexUIState<>(Set.copyOf(initial));
    }

    /** Returns immutable selection snapshots that can be bound during render. */
    public VexUIObservable<Set<String>> selected() {
        return selected.readOnly();
    }

    /** Selects an available option, toggling its membership for a multiple-selection group. */
    public void select(final String id) {
        var option = options.stream().filter(value -> value.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown option " + id));
        if (!option.enabled()) {
            return;
        }
        selected.update(previous -> {
            if (!multiple) {
                return Set.of(id);
            }
            Set<String> next = new HashSet<>(previous);
            if (!next.remove(id)) {
                next.add(id);
            }
            return Set.copyOf(next);
        });
    }

    /** Builds themed choice tiles and observes this selection for the current view. */
    public VexUIGrid controls(
        final VexUISession session,
        final String prefix,
        final int columns,
        final Map<String, VexUIAction> actions
    ) {
        var style = session.styleSheet();
        return new VexUIGrid(VexUISize.content(), columns, style.gap(),
            VexUITabs.buttons(prefix, options, session.bind(selected()), this::select, style, actions));
    }

    /** Builds horizontal tabs and observes this selection for the current view. */
    public VexUIRow tabs(final VexUISession session, final String prefix, final Map<String, VexUIAction> actions) {
        return VexUITabs.controls(prefix, options, session.bind(selected()), this::select, session.styleSheet(), actions);
    }
}
