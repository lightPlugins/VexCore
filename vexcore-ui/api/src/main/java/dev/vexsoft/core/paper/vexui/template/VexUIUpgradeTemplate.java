package dev.vexsoft.core.paper.vexui.template;

import dev.vexsoft.core.paper.vexui.VexUIAction;
import dev.vexsoft.core.paper.vexui.VexUIActions;
import dev.vexsoft.core.paper.vexui.VexUIActionStatus;
import dev.vexsoft.core.paper.vexui.VexUIAsyncAction;
import dev.vexsoft.core.paper.vexui.VexUIButtonRole;
import dev.vexsoft.core.paper.vexui.VexUIColumn;
import dev.vexsoft.core.paper.vexui.VexUIElement;
import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.VexUINavigation;
import dev.vexsoft.core.paper.vexui.VexUIPagedGrid;
import dev.vexsoft.core.paper.vexui.VexUIParagraph;
import dev.vexsoft.core.paper.vexui.VexUIProgressBar;
import dev.vexsoft.core.paper.vexui.VexUIRow;
import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.VexUISize;
import dev.vexsoft.core.paper.vexui.VexUIText;
import dev.vexsoft.core.paper.vexui.VexUIView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

/** Upgrade selector with before/after preview, paginated costs, progress, and single-flight execution. */
public final class VexUIUpgradeTemplate {

    /** Creates an independent root and closes its adapter when the session retires. */
    public VexUIView createView(final VexUIUpgradeProvider provider) {
        return new View(Objects.requireNonNull(provider, "provider"));
    }

    /** Current item and available upgrade choices, including locked choices for preview. */
    public record Snapshot(Component title, Component current, VexUIIcon icon, List<Option> options) {
        /** Copies options and rejects duplicate action identities. */
        public Snapshot {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(current, "current");
            Objects.requireNonNull(icon, "icon");
            options = List.copyOf(options);
            if (options.stream().map(Option::id).distinct().count() != options.size()) {
                throw new IllegalArgumentException("Duplicate upgrade option");
            }
        }
    }

    /** An upgrade's progress can represent satisfied requirements or a domain-defined completion value. */
    public record Option(
        String id,
        Component name,
        List<Component> preview,
        List<Requirement> requirements,
        double progress,
        Component action,
        boolean enabled
    ) {
        /** Copies previews and requirements and clamps finite progress to the supported range. */
        public Option {
            if (id == null || id.isBlank() || !Double.isFinite(progress)) {
                throw new IllegalArgumentException("Invalid upgrade option");
            }
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(action, "action");
            preview = List.copyOf(preview);
            requirements = List.copyOf(requirements);
            progress = Math.clamp(progress, 0, 1);
        }
    }

    /** A cost or requirement retains its plugin-localized amount, state and item icon. */
    public record Requirement(Component name, List<Component> details, VexUIIcon icon) {
        /** Copies complete requirement details and validates presentation values. */
        public Requirement {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(icon, "icon");
            details = List.copyOf(details);
        }
    }

    private static final class View implements VexUIView {
        private final VexUIUpgradeProvider provider;
        private final VexUIAsyncAction<Component> operation = new VexUIAsyncAction<>();
        private String selected;
        private int optionsPage;
        private int requirementsPage;
        private Component message = Component.empty();

        private View(final VexUIUpgradeProvider provider) {
            this.provider = provider;
        }

        @Override
        public VexUIFrame render(final VexUISession session) {
            var snapshot = session.bind(provider);
            var layout = provider.layout();
            boolean running = session.bind(operation.status()) == VexUIActionStatus.RUNNING;
            Map<String, VexUIAction> actions = new LinkedHashMap<>();
            List<VexUIElement> content = new ArrayList<>();
            content.add(new VexUIRow(6, snapshot.icon().at(32), new VexUIText(VexUISize.flexible(), snapshot.current(), false)));
            var current = VexUIPagedGrid.slice(snapshot.options(), optionsPage, layout.columns());
            optionsPage = current.page();
            content.add(current.grid(layout.columns(), layout.gap(), 24, value -> {
                String id = "option." + value.id();
                VexUIActions.register(actions, id, ignored -> {
                    selected = value.id();
                    requirementsPage = 0;
                });
                return VexUITemplateControls.button(session, value.name(), id, !running)
                    .withRole(value.id().equals(selected) ? VexUIButtonRole.SELECTED : VexUIButtonRole.NORMAL);
            }));
            if (current.pages() > 1) {
                VexUIActions.register(actions, "options.previous", ignored -> optionsPage = Math.max(0, optionsPage - 1));
                VexUIActions.register(actions, "options.next", ignored -> optionsPage = Math.min(current.pages() - 1, optionsPage + 1));
                content.add(new VexUIRow(4,
                    VexUITemplateControls.button(session, provider.text("previous"), "options.previous", optionsPage > 0),
                    VexUITemplateControls.button(session, provider.text("next"), "options.next", optionsPage + 1 < current.pages())));
            }
            var option = snapshot.options().stream().filter(value -> value.id().equals(selected)).findFirst()
                .orElse(snapshot.options().isEmpty() ? null : snapshot.options().getFirst());
            if (option == null) {
                content.add(new VexUIText(provider.text("empty")));
            } else {
                selected = option.id();
                content.add(new VexUIText(option.name()));
                content.add(new VexUIParagraph(VexUISize.content(), Component.join(JoinConfiguration.spaces(), option.preview()), 4));
                content.add(VexUIProgressBar.of(option.progress()));
                var requirements = VexUIPagedGrid.slice(option.requirements(), requirementsPage, layout.capacity());
                requirementsPage = requirements.page();
                content.add(requirements.grid(layout.columns(), layout.gap(), layout.cellHeight(), value ->
                    new VexUIColumn(2, new VexUIRow(3, value.icon().at(16),
                        new VexUIText(VexUISize.flexible(), value.name(), false)),
                        new VexUIParagraph(VexUISize.content(), Component.join(JoinConfiguration.spaces(), value.details()), 2))));
                content.add(VexUITemplateControls.page(session, requirements, provider, actions, value -> requirementsPage = value));
                VexUIActions.register(actions, "upgrade", viewer -> {
                    var latest = provider.get().options().stream().filter(value -> value.id().equals(option.id()))
                        .findFirst().orElse(null);
                    if (latest != null && latest.enabled()) {
                        operation.run(viewer, () -> provider.upgrade(option.id()), (active, result) -> message = result,
                            (active, failure) -> message = provider.text("failed"));
                    }
                });
                content.add(VexUITemplateControls.button(session, running ? provider.text("pending") : option.action(),
                    "upgrade", !running && option.enabled()).withRole(VexUIButtonRole.PRIMARY));
            }
            content.add(new VexUIParagraph(message));
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
