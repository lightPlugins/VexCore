package dev.vexsoft.core.paper.vexui.template;

import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.VexUIView;
import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.action.VexUIActions;
import dev.vexsoft.core.paper.vexui.component.VexUINavigation;
import dev.vexsoft.core.paper.vexui.component.VexUIPagedGrid;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIColumn;
import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import dev.vexsoft.core.paper.vexui.element.VexUIParagraph;
import dev.vexsoft.core.paper.vexui.element.VexUIRow;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.style.VexUIAttention;
import dev.vexsoft.core.paper.vexui.style.VexUIButtonRole;
import dev.vexsoft.core.paper.vexui.template.internal.VexUITemplateControls;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

/** Paginated dashboard tiles with plugin actions, disabled states, and reward/news attention. */
public final class VexUIMainMenuTemplate {

    /** Creates a fresh root; child navigation preserves its page and closes the adapter only with the root. */
    public VexUIView createView(final VexUITemplateProvider<Snapshot> provider) {
        return new View(Objects.requireNonNull(provider, "provider"));
    }

    /** Immutable dashboard presentation. */
    public record Snapshot(Component title, Component subtitle, List<Entry> entries) {
        /** Copies the ordered tiles and rejects ambiguous action identities. */
        public Snapshot {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(subtitle, "subtitle");
            entries = List.copyOf(entries);
            if (entries.stream().map(Entry::id).distinct().count() != entries.size()) {
                throw new IllegalArgumentException("Duplicate dashboard entry");
            }
        }
    }

    /** One tile; the action can navigate to another reusable template. */
    public record Entry(
        String id,
        Component title,
        List<Component> details,
        VexUIIcon icon,
        boolean enabled,
        VexUIAttention attention,
        VexUIAction action
    ) {
        /** Copies complete detail lines and validates required presentation and action values. */
        public Entry {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Missing dashboard entry ID");
            }
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(attention, "attention");
            Objects.requireNonNull(action, "action");
            details = List.copyOf(details);
        }
    }

    private static final class View implements VexUIView {
        private final VexUITemplateProvider<Snapshot> provider;
        private int page;

        private View(final VexUITemplateProvider<Snapshot> provider) {
            this.provider = provider;
        }

        @Override
        public VexUIFrame render(final VexUISession session) {
            var snapshot = session.bind(provider);
            var layout = provider.layout();
            var current = VexUIPagedGrid.slice(snapshot.entries(), page, layout.capacity());
            page = current.page();
            Map<String, VexUIAction> actions = new LinkedHashMap<>();
            List<VexUIElement> content = new ArrayList<>();
            content.add(new VexUIParagraph(snapshot.subtitle()));
            content.add(current.grid(layout.columns(), layout.gap(), layout.cellHeight(), entry -> {
                String id = "entry." + entry.id();
                VexUIActions.register(actions, id, viewer -> {
                    if (entry.enabled()) {
                        entry.action().execute(viewer);
                    }
                });
                var label = new VexUIColumn(2,
                    new VexUIRow(4, entry.icon().at(16), new VexUIText(VexUISize.flexible(), entry.title(), false)),
                    new VexUIParagraph(VexUISize.content(), Component.join(JoinConfiguration.spaces(), entry.details()), 2));
                return new VexUIButton(new VexUISize(-1, layout.cellHeight(), 0), label, session.styleSheet().normal(),
                    entry.enabled(), id, Component.join(JoinConfiguration.newlines(), entry.details()))
                    .withRole(VexUIButtonRole.NORMAL).withAttention(entry.attention());
            }));
            content.add(VexUITemplateControls.page(session, current, provider, actions, value -> page = value));
            content.add(VexUINavigation.back(session, provider.text("back"), actions));
            return new VexUIFrame(snapshot.title(), provider.text("close"), layout.width(),
                new VexUIColumn(VexUISize.content(), layout.gap(), content), actions);
        }

        @Override
        public void closed() {
            provider.close();
        }
    }
}
