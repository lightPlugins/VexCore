package dev.vexsoft.core.paper.vexui.template.profession;

import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.VexUIView;
import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.action.VexUIActionStatus;
import dev.vexsoft.core.paper.vexui.action.VexUIActions;
import dev.vexsoft.core.paper.vexui.action.VexUIAsyncAction;
import dev.vexsoft.core.paper.vexui.action.VexUIClickSound;
import dev.vexsoft.core.paper.vexui.component.VexUINavigation;
import dev.vexsoft.core.paper.vexui.component.VexUIPagedGrid;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIColumn;
import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import dev.vexsoft.core.paper.vexui.element.VexUIGrid;
import dev.vexsoft.core.paper.vexui.element.VexUIGridCell;
import dev.vexsoft.core.paper.vexui.element.VexUIPanel;
import dev.vexsoft.core.paper.vexui.element.VexUIParagraph;
import dev.vexsoft.core.paper.vexui.element.VexUIProgressBar;
import dev.vexsoft.core.paper.vexui.element.VexUIRow;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUISpacer;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.style.VexUIAttention;
import dev.vexsoft.core.paper.vexui.style.VexUIButtonRole;
import dev.vexsoft.core.paper.vexui.style.VexUITheme;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Claim;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Entry;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Milestone;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Profession;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Status;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Single profession workspace with a compact sidebar, embedded actions, and inline previews and receipts. */
public final class VexUIProfessionTemplate {

    /** Creates a fresh workspace owning the supplied viewer adapter. */
    public VexUIView createView(final VexUIProfessionProvider provider) {
        return new View(Objects.requireNonNull(provider, "provider"), null);
    }

    /** Opens the same workspace with the station's profession selected. */
    public VexUIView createView(final VexUIProfessionProvider provider, final String profession) {
        return new View(Objects.requireNonNull(provider, "provider"), profession);
    }

    private static final class JobState {
        private String tab = "levels";
        private int selectedLevel;
        private int itemPage;
        private String selectedEntry;
        private int detailPage;
        private final Map<String, Integer> pages = new HashMap<>();
    }

    private record Workspace(VexUIElement content, VexUIElement navigation) {
    }

    private static final class View implements VexUIView {
        private final VexUIProfessionProvider provider;
        private final Map<String, JobState> jobs = new HashMap<>();
        private final Set<String> seenRequests = new HashSet<>();
        private final VexUIAsyncAction<Claim> claims = new VexUIAsyncAction<>();
        private String profession;
        private int professionPage;
        private int filter;
        private int sort;
        private boolean locateSelection = true;
        private Claim receipt;
        private boolean showReceipt;
        private int receiptPage;
        private int receiptDetailPage;
        private String receiptEntry;
        private Component message = Component.empty();

        private View(final VexUIProfessionProvider provider, final String profession) {
            this.provider = provider;
            this.profession = profession;
        }

        @Override
        public VexUIFrame render(final VexUISession session) {
            var snapshot = session.bind(provider);
            var layout = provider.layout("main");
            if (!snapshot.valid()) {
                session.close();
                return new VexUIFrame(t("main.title"), t("main.close"), layout.width(),
                    new VexUISpacer(VexUISize.fixed(1, 1)), Map.of());
            }
            boolean claiming = session.bind(claims.status()) == VexUIActionStatus.RUNNING;
            boolean running = claiming || snapshot.pending();
            var job = snapshot.professions().stream().filter(value -> value.id().equals(profession)).findFirst()
                .orElse(snapshot.professions().isEmpty() ? null : snapshot.professions().getFirst());
            profession = job == null ? null : job.id();
            Map<String, VexUIAction> actions = new LinkedHashMap<>();
            var global = collect(session, "claim.all", null, null, snapshot.available(), running,
                "main.collect_all", actions).withSize(new VexUISize(236, 22, 0));
            List<VexUIElement> content = new ArrayList<>();
            List<VexUIElement> header = new ArrayList<>();
            header.add(new VexUIText(VexUISize.flexible(), message.equals(Component.empty())
                ? t("main.global_summary", Map.of(
                "available", Integer.toString(snapshot.available()),
                "professions", Integer.toString(snapshot.professions().size()))) : message, false));
            if (receipt != null) {
                action(actions, "receipt.toggle", ignored -> showReceipt = !showReceipt);
                header.add(button(session, t(showReceipt ? "receipt.return" : "receipt.toggle"),
                    "receipt.toggle", true, VexUIAttention.none(), 80));
            }
            header.add(global);
            var back = VexUINavigation.back(session, t("main.back"), actions);
            header.add(back.withSize(new VexUISize(back.size().width(), 22, 0)));
            content.add(new VexUIRow(VexUISize.content(), 4, header));
            Workspace workspace;
            if (showReceipt && receipt != null) {
                workspace = receipt(session, actions);
            } else if (job != null) {
                workspace = job(session, job, running, actions);
            } else {
                workspace = new Workspace(new VexUIParagraph(t("main.empty")),
                    new VexUISpacer(new VexUISize(-1, 22, 0)));
            }
            var sidebar = sidebar(session, snapshot, actions);
            // Navigation is a separate shared row, so every pager has the same baseline regardless of content height.
            content.add(new VexUIColumn(VexUISize.content(), 3, List.of(
                workspaceRow(sidebar.content(), workspace.content()),
                workspaceRow(sidebar.navigation(), workspace.navigation()))));
            return new VexUIFrame(t("main.title"), t("main.close"), layout.width(),
                new VexUIPanel(VexUISize.content(), 6, session.styleSheet().panel(),
                    new VexUIColumn(VexUISize.content(), 4, content)), actions);
        }

        @Override
        public void closed() {
            provider.close();
        }

        private Workspace sidebar(
            final VexUISession session,
            final VexUIProfessionSnapshot snapshot,
            final Map<String, VexUIAction> actions
        ) {
            action(actions, "filter", ignored -> {
                filter = (filter + 1) % 3;
                professionPage = 0;
            });
            action(actions, "sort", ignored -> {
                sort = (sort + 1) % 3;
                professionPage = 0;
            });
            Comparator<Profession> order = sort == 1 ? Comparator.comparing(value -> plain(value.name()))
                : sort == 2 ? Comparator.comparingInt(Profession::level).reversed()
                : Comparator.comparingInt(snapshot.professions()::indexOf);
            var filtered = snapshot.professions().stream().filter(value -> filter != 1 || value.available() > 0)
                .filter(value -> filter != 2 || value.fresh())
                .sorted(order).toList();
            var layout = provider.layout("main");
            if (locateSelection) {
                int index = IntStream.range(0, filtered.size()).filter(value -> filtered.get(value).id().equals(profession))
                    .findFirst().orElse(0);
                professionPage = index / layout.capacity();
                locateSelection = false;
            }
            var current = VexUIPagedGrid.slice(filtered, professionPage, layout.capacity());
            professionPage = current.page();
            List<VexUIElement> content = new ArrayList<>();
            content.add(new VexUIRow(3,
                button(session, t("main.filter_" + filter), "filter", true, VexUIAttention.none(), -1),
                button(session, t("main.sort_" + sort), "sort", true, VexUIAttention.none(), -1)));
            var grid = current.grid(1, 2, layout.cellHeight(), value -> {
                String id = "profession." + value.id();
                action(actions, id, ignored -> {
                    profession = value.id();
                    showReceipt = false;
                });
                var line = new VexUISize(-1, 9, 0);
                var label = new VexUIRow(4, value.icon().at(16), new VexUIColumn(VexUISize.flexible(), 0,
                    List.of(new VexUIText(line, value.name(), false),
                        new VexUIText(line, t("main.progress", values(value)), false))));
                return new VexUIButton(new VexUISize(-1, layout.cellHeight(), 0), label, session.styleSheet().normal(),
                    true, id, lore("main.profession_lore", values(value), Map.of("name", List.of(value.name()),
                        "description", value.description()))).withRole(value.id().equals(profession)
                            ? VexUIButtonRole.SELECTED : VexUIButtonRole.NORMAL)
                    .withAttention(new VexUIAttention(value.available(), value.fresh()));
            });
            if (filtered.isEmpty()) {
                int height = layout.capacity() * layout.cellHeight() + Math.max(0, layout.capacity() - 1) * 2;
                content.add(new VexUIColumn(new VexUISize(-1, height, 0), 0,
                    List.of(new VexUIParagraph(VexUISize.content(), t("main.empty"), 2))));
            } else {
                content.add(grid);
            }
            return new Workspace(new VexUIColumn(new VexUISize(-1, workspaceHeight(), 0), 3, content),
                pager(session, "professions", current, actions, value -> professionPage = value));
        }

        private Workspace job(
            final VexUISession session,
            final Profession job,
            final boolean running,
            final Map<String, VexUIAction> actions
        ) {
            List<VexUIElement> content = new ArrayList<>();
            var state = jobs.computeIfAbsent(job.id(), ignored -> new JobState());
            if (state.selectedLevel == 0) {
                state.selectedLevel = job.milestones().stream().filter(value -> value.status() == Status.AVAILABLE)
                    .mapToInt(Milestone::level).min().orElse(Math.min(job.maximum(), job.level() + 1));
                state.pages.put("levels", (state.selectedLevel - 1) / provider.layout("levels").capacity());
            }
            state.selectedLevel = Math.clamp(state.selectedLevel, 1, job.maximum());
            var heading = new VexUIColumn(VexUISize.flexible(), 2, List.of(new VexUIText(job.name()),
                new VexUIText(t(job.level() == job.maximum() ? "job.maximum" : "job.progress", values(job))),
                new VexUIProgressBar(new VexUISize(-1, 5, 0), job.progress(), 0,
                    session.styleSheet().selected().lightBorder(), session.styleSheet().normal().background())));
            content.add(new VexUIPanel(VexUISize.content(), 4, session.styleSheet().normal(),
                    new VexUIRow(6, job.icon().at(32), heading))
                .withTooltip(lore("main.profession_lore", values(job),
                    Map.of("name", List.of(job.name()), "description", job.description()))));
            List<VexUIElement> tabs = new ArrayList<>();
            for (String tab : List.of("levels", "rewards", "sources", "unlocks")) {
                action(actions, "tab." + tab, ignored -> state.tab = tab);
                tabs.add(button(session, t("job.tab_" + tab, Map.of("available", Integer.toString(job.available()))),
                    "tab." + tab, true, new VexUIAttention(tab.equals("rewards") ? job.available() : 0,
                        tab.equals("unlocks") && job.fresh()), -1)
                    .withRole(state.tab.equals(tab) ? VexUIButtonRole.SELECTED : VexUIButtonRole.NORMAL));
            }
            content.add(new VexUIRow(VexUISize.content(), 3, tabs));
            var section = switch (state.tab) {
                case "levels" -> levels(session, job, state, actions);
                case "rewards" -> rewards(session, job, state, running, actions);
                default -> entries(session, job, state, actions);
            };
            content.add(section.content());
            return new Workspace(new VexUIColumn(VexUISize.content(), 4, content), section.navigation());
        }

        private Workspace levels(
            final VexUISession session,
            final Profession job,
            final JobState state,
            final Map<String, VexUIAction> actions
        ) {
            var layout = provider.layout("levels");
            int pages = Math.max(1, Math.ceilDiv(job.maximum(), layout.capacity()));
            int page = Math.clamp(state.pages.getOrDefault("levels", 0), 0, pages - 1);
            var numbers = IntStream.rangeClosed(page * layout.capacity() + 1,
                Math.min(job.maximum(), (page + 1) * layout.capacity())).boxed().toList();
            var current = new VexUIPagedGrid<>(numbers, page, pages, job.maximum(), layout.capacity());
            state.pages.put("levels", page);
            List<VexUIElement> list = new ArrayList<>();
            list.add(current.grid(1, 2, layout.cellHeight(), level -> {
                var milestone = milestone(job, level);
                var status = milestone == null ? level <= job.level() ? Status.REACHED : Status.LOCKED : milestone.status();
                String id = "level." + level;
                action(actions, id, ignored -> select(state, level));
                var label = new VexUIRow(3, new VexUIText(new VexUISize(43, -1, 0),
                    t("levels.level", Map.of("level", level.toString())), false), new VexUIText(VexUISize.flexible(),
                    t("levels." + status.name().toLowerCase(Locale.ROOT)), false));
                var rewards = milestone == null ? List.of(t("levels.no_reward")) : rewardNames(milestone);
                return new VexUIButton(new VexUISize(-1, layout.cellHeight(), 0), label, session.styleSheet().normal(),
                    true, id, lore("levels.level_lore", Map.of("level", level.toString()), Map.of("rewards", rewards)))
                    .withRole(level == state.selectedLevel ? VexUIButtonRole.SELECTED : VexUIButtonRole.NORMAL)
                    .withAttention(new VexUIAttention(status == Status.AVAILABLE ? 1 : 0, false));
            }));
            var levels = new Workspace(new VexUIColumn(VexUISize.content(), 3, list),
                pager(session, "page", current, actions, value -> state.pages.put("levels", value)));
            return split(levels, preview(session, job, state, actions));
        }

        private Workspace rewards(
            final VexUISession session,
            final Profession job,
            final JobState state,
            final boolean running,
            final Map<String, VexUIAction> actions
        ) {
            var layout = provider.layout("rewards");
            var current = VexUIPagedGrid.slice(job.milestones(), state.pages.getOrDefault("rewards", 0), layout.capacity());
            state.pages.put("rewards", current.page());
            if (milestone(job, state.selectedLevel) == null && !current.entries().isEmpty()) {
                select(state, current.entries().getFirst().level());
            }
            List<VexUIElement> list = new ArrayList<>();
            list.add(current.grid(1, 3, layout.cellHeight(), value -> {
                String id = "bundle." + value.level();
                action(actions, id, ignored -> select(state, value.level()));
                Component tooltip = rewardLore(value);
                var label = new VexUIColumn(1,
                    new VexUIText(t("rewards.level", Map.of("level", Integer.toString(value.level())))),
                    new VexUIText(t("rewards." + value.status().name().toLowerCase(Locale.ROOT),
                        Map.of("count", Integer.toString(value.rewards().size())))));
                var select = new VexUIButton(new VexUISize(-1, layout.cellHeight() - 6, 1), label,
                    session.styleSheet().normal(), true, id, tooltip)
                    .withRole(value.level() == state.selectedLevel ? VexUIButtonRole.SELECTED : VexUIButtonRole.NORMAL)
                    .withAttention(new VexUIAttention(value.status() == Status.AVAILABLE ? 1 : 0, false));
                var claim = collect(session, "claim.level." + value.level(), job.id(), value.level(),
                    value.status() == Status.AVAILABLE ? 1 : 0, running, "rewards.collect_level", actions);
                var caption = value.status() == Status.AVAILABLE || running ? claim.content()
                    : new VexUIText(t(value.status() == Status.CLAIMED ? "rewards.claimed_button" : "rewards.locked_button"));
                claim = new VexUIButton(new VexUISize(59, 22, 0), caption, claim.theme(), claim.enabled(),
                    claim.action(), tooltip).withRole(VexUIButtonRole.PRIMARY).withAttention(claim.attention());
                return new VexUIPanel(new VexUISize(-1, layout.cellHeight(), 0), 3, session.styleSheet().normal(),
                    new VexUIRow(3, select, claim)).withTooltip(tooltip);
            }));
            list.add(collect(session, "claim.job", job.id(), null, job.available(), running,
                "rewards.collect_job", actions));
            var rewards = new Workspace(new VexUIColumn(VexUISize.content(), 3, list),
                pager(session, "page", current, actions, value -> state.pages.put("rewards", value)));
            return split(rewards, preview(session, job, state, actions));
        }

        private Workspace preview(
            final VexUISession session,
            final Profession job,
            final JobState state,
            final Map<String, VexUIAction> actions
        ) {
            List<VexUIElement> content = new ArrayList<>();
            var milestone = milestone(job, state.selectedLevel);
            content.add(new VexUIText(t("levels.preview", Map.of("level", Integer.toString(state.selectedLevel)))));
            var items = milestone == null ? List.<Entry>of() : milestone.rewards();
            var current = VexUIPagedGrid.slice(items, state.itemPage, 4);
            state.itemPage = current.page();
            if (milestone == null) {
                content.add(new VexUIColumn(new VexUISize(-1, 102, 0), 0,
                    List.of(new VexUIText(t("levels.no_reward")))));
            } else {
                content.add(current.grid(1, 2, 24, value -> item(session, value, 24, "rewards", actions)));
            }
            return new Workspace(new VexUIColumn(VexUISize.content(), 3, content),
                pager(session, "items", current, actions, value -> state.itemPage = value));
        }

        private Workspace entries(
            final VexUISession session,
            final Profession job,
            final JobState state,
            final Map<String, VexUIAction> actions
        ) {
            var layout = provider.layout(state.tab);
            List<Entry> values = state.tab.equals("sources") ? job.sources() : job.unlocks();
            boolean unlocks = state.tab.equals("unlocks");
            var current = VexUIPagedGrid.slice(values, state.pages.getOrDefault(state.tab, 0),
                layout.capacity() * (unlocks ? 2 : 1));
            state.pages.put(state.tab, current.page());
            List<VexUIElement> list = new ArrayList<>();
            list.add(current.grid(unlocks ? 2 : 1, unlocks ? 6 : 2, layout.cellHeight(), value -> {
                String id = "entry." + value.id();
                if (!unlocks) {
                    action(actions, id, ignored -> {
                        state.selectedEntry = value.id();
                        state.detailPage = 0;
                    });
                }
                return item(session, value, layout.cellHeight(), state.tab, actions);
            }));
            var navigation = pager(session, "page", current, actions, value -> {
                state.pages.put(state.tab, value);
                state.selectedEntry = null;
                state.detailPage = 0;
            });
            var section = new Workspace(new VexUIColumn(VexUISize.content(), 3, list), navigation);
            if (!unlocks) {
                var selected = values.stream().filter(value -> value.id().equals(state.selectedEntry)).findFirst()
                    .orElse(current.entries().isEmpty() ? null : current.entries().getFirst());
                section = split(section, detail(session, selected, "details", state.detailPage,
                    value -> state.detailPage = value, actions));
            }
            if (state.tab.equals("unlocks")) {
                var seen = current.entries().stream().filter(value -> value.available() && value.fresh())
                    .map(Entry::id).filter(value -> seenRequests.add(value)).toList();
                if (!seen.isEmpty()) {
                    var scope = session.scope();
                    scope.update(() -> provider.seen(seen).whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            scope.update(() -> message = t("main.failed"));
                        }
                    }));
                }
            }
            return section;
        }

        private VexUIElement item(
            final VexUISession session,
            final Entry entry,
            final int height,
            final String section,
            final Map<String, VexUIAction> actions
        ) {
            String id = "entry." + entry.id();
            var label = new VexUIRow(3, entry.icon().at(16), new VexUIText(VexUISize.flexible(), entry.name(), false));
            var details = entry.details().stream().filter(line -> !line.equals(entry.name())).toList();
            var tooltip = lore(section + (section.equals("rewards") ? ".item_lore" : ".entry_lore"),
                Map.of("level", Integer.toString(entry.requiredLevel())),
                Map.of("name", List.of(entry.name()), "details", details));
            if (actions.containsKey(id)) {
                return new VexUIButton(new VexUISize(-1, height, 0), label, session.styleSheet().normal(),
                    true, id, tooltip).withRole(VexUIButtonRole.NORMAL)
                    .withAttention(new VexUIAttention(0, entry.available() && entry.fresh()));
            }
            // Item previews have no navigation action; their complete formatted description stays on hover.
            var theme = session.styleSheet().normal();
            if (entry.available() && entry.fresh()) {
                theme = new VexUITheme(theme.background(), theme.outerBorder(),
                    0xC5A164, 0x82683F);
            }
            return new VexUIPanel(new VexUISize(-1, height, 0), 3, theme,
                new VexUIRow(3, entry.icon().at(16), new VexUIText(VexUISize.flexible(), entry.name(), false)))
                .withTooltip(tooltip);
        }

        private Workspace detail(
            final VexUISession session,
            final Entry entry,
            final String prefix,
            final int page,
            final IntConsumer changed,
            final Map<String, VexUIAction> actions
        ) {
            List<VexUIElement> content = new ArrayList<>();
            if (entry == null) {
                content.add(new VexUIParagraph(t("main.empty")));
                return new Workspace(new VexUIColumn(VexUISize.content(), 3, content),
                    pager(session, prefix, VexUIPagedGrid.slice(List.of(), 0, 5), actions, changed));
            }
            content.add(new VexUIRow(4, entry.icon().at(32), new VexUIParagraph(VexUISize.flexible(), entry.name(), 2)));
            int width = Math.max(40, (provider.layout("main").width() - 48) * 3 / 8 - 8);
            var lines = entry.details().stream().flatMap(value -> session.wrap(value, width).stream()).toList();
            var current = VexUIPagedGrid.slice(lines, page, 5);
            List<VexUIElement> text = new ArrayList<>();
            current.entries().forEach(value -> text.add(new VexUIText(value)));
            while (text.size() < 5) {
                text.add(new VexUISpacer(new VexUISize(-1, 12, 0)));
            }
            content.add(new VexUIPanel(VexUISize.content(), 4, session.styleSheet().normal(),
                new VexUIColumn(VexUISize.content(), 1, text)));
            return new Workspace(new VexUIColumn(VexUISize.content(), 3, content),
                pager(session, prefix, current, actions, changed));
        }

        private Workspace receipt(
            final VexUISession session,
            final Map<String, VexUIAction> actions
        ) {
            List<VexUIElement> content = new ArrayList<>();
            List<Entry> entries = new ArrayList<>();
            for (int index = 0; index < receipt.receipts().size(); index++) {
                var grant = receipt.receipts().get(index);
                for (var item : grant.rewards()) {
                    List<Component> details = new ArrayList<>();
                    details.add(t("receipt.origin", Map.of("profession", plain(grant.name()),
                        "level", Integer.toString(grant.level()))));
                    details.addAll(item.details());
                    entries.add(new Entry("receipt_" + index + "_" + item.id(), item.name(), details, item.icon()));
                }
            }
            var layout = provider.layout("receipt");
            var current = VexUIPagedGrid.slice(entries, receiptPage, layout.capacity());
            receiptPage = current.page();
            content.add(new VexUIText(t("receipt.summary", Map.of("count", Integer.toString(receipt.receipts().size())))));
            List<VexUIElement> list = new ArrayList<>();
            list.add(current.grid(1, 2, layout.cellHeight(), entry -> {
                String id = "entry." + entry.id();
                action(actions, id, ignored -> {
                    receiptEntry = entry.id();
                    receiptDetailPage = 0;
                });
                return item(session, entry, layout.cellHeight(), "receipt", actions);
            }));
            var navigation = pager(session, "receipt", current, actions, value -> {
                receiptPage = value;
                receiptEntry = null;
                receiptDetailPage = 0;
            });
            var selected = entries.stream().filter(value -> value.id().equals(receiptEntry)).findFirst()
                .orElse(current.entries().isEmpty() ? null : current.entries().getFirst());
            var section = split(new Workspace(new VexUIColumn(VexUISize.content(), 3, list), navigation),
                detail(session, selected, "details", receiptDetailPage, value -> receiptDetailPage = value, actions));
            content.add(section.content());
            return new Workspace(new VexUIColumn(VexUISize.content(), 4, content), section.navigation());
        }

        private VexUIButton collect(
            final VexUISession session,
            final String id,
            final String job,
            final Integer level,
            final int available,
            final boolean running,
            final String label,
            final Map<String, VexUIAction> actions
        ) {
            action(actions, id, viewer -> {
                if (available == 0 || running) {
                    return;
                }
                message = Component.empty();
                claims.run(viewer, () -> provider.claim(job, level), (active, result) -> {
                    if (result.successful()) {
                        if (!result.receipts().isEmpty()) {
                            active.player().playSound(VexUIClickSound.collect().asSound());
                        }
                        receipt = result;
                        receiptPage = 0;
                        receiptEntry = null;
                        message = t("receipt.summary", Map.of("count", Integer.toString(result.receipts().size())));
                    } else {
                        message = t("main.failed");
                    }
                }, (active, failure) -> message = t("main.failed"));
            });
            String key = running ? "main.pending" : available == 0 ? "main.nothing" : label;
            return button(session, t(key, Map.of("available", Integer.toString(available))), id,
                available > 0 && !running, new VexUIAttention(available, false), -1)
                .withRole(VexUIButtonRole.PRIMARY);
        }

        private VexUIElement pager(
            final VexUISession session,
            final String prefix,
            final VexUIPagedGrid<?> current,
            final Map<String, VexUIAction> actions,
            final IntConsumer changed
        ) {
            action(actions, prefix + ".previous", ignored -> changed.accept(Math.max(0, current.page() - 1)));
            action(actions, prefix + ".next", ignored -> changed.accept(Math.min(current.pages() - 1, current.page() + 1)));
            return new VexUIRow(3,
                button(session, t("main.previous"), prefix + ".previous", current.page() > 0, VexUIAttention.none(), 24),
                new VexUIText(VexUISize.flexible(), t("main.page", Map.of("page", Integer.toString(current.page() + 1),
                    "pages", Integer.toString(current.pages()))), true),
                button(session, t("main.next"), prefix + ".next", current.page() + 1 < current.pages(),
                    VexUIAttention.none(), 24));
        }

        private VexUIButton button(
            final VexUISession session,
            final Component label,
            final String id,
            final boolean enabled,
            final VexUIAttention attention,
            final int width
        ) {
            String hint = id.equals("filter") ? "main.filter_hint"
                : id.equals("sort") ? "main.sort_hint"
                : id.startsWith("tab.") ? "job.tab_hint" : id.equals("claim.all") ? "main.collect_hint"
                : id.equals("claim.job") ? "rewards.job_hint" : id.equals("receipt.toggle") ? "receipt.toggle_hint"
                : "main.page_hint";
            boolean centered = id.equals("filter") || id.equals("sort") || id.startsWith("tab.")
                || id.endsWith(".previous") || id.endsWith(".next");
            var text = new VexUIText(new VexUISize(-1, centered ? 9 : 12, 0), label, centered);
            return new VexUIButton(new VexUISize(width, 22, width < 0 ? 1 : 0), text,
                session.styleSheet().normal(), enabled, id, lore("main.control_lore", Map.of(),
                    Map.of("name", List.of(label), "details", List.of(t(hint))))).withRole(VexUIButtonRole.NORMAL)
                .withAttention(attention);
        }

        private Workspace split(final Workspace left, final Workspace right) {
            return new Workspace(new VexUIGrid(VexUISize.content(), 2, 6, List.of(left.content(), right.content())),
                new VexUIGrid(VexUISize.content(), 2, 6, List.of(left.navigation(), right.navigation())));
        }

        private VexUIElement workspaceRow(final VexUIElement sidebar, final VexUIElement workspace) {
            return new VexUIGrid(VexUISize.content(), 4, 8, List.of(new VexUIGridCell(1, 1, sidebar),
                new VexUIGridCell(3, 1, workspace)));
        }

        private int workspaceHeight() {
            // Reserve the tallest section before laying out the shared pager row, including on empty pages.
            int sidebar = 22 + 3 + gridHeight("main", 2);
            int lists = Math.max(gridHeight("levels", 2), Math.max(gridHeight("sources", 2),
                Math.max(gridHeight("unlocks", 6), gridHeight("rewards", 3) + 3 + 22)));
            int job = 41 + 4 + 22 + 4 + Math.max(lists, 117);
            int receipt = 12 + 4 + Math.max(gridHeight("receipt", 2), 107);
            return Math.max(sidebar, Math.max(job, receipt));
        }

        private int gridHeight(final String section, final int gap) {
            var layout = provider.layout(section);
            return layout.capacity() * layout.cellHeight() + Math.max(0, layout.capacity() - 1) * gap;
        }

        private Component rewardLore(final Milestone milestone) {
            return lore("rewards.bundle_lore", Map.of("level", Integer.toString(milestone.level()),
                "count", Integer.toString(milestone.rewards().size())),
                Map.of("rewards", rewardNames(milestone)));
        }

        private List<Component> rewardNames(final Milestone milestone) {
            List<Component> names = new ArrayList<>(milestone.rewards().stream().limit(5).map(Entry::name).toList());
            if (milestone.rewards().size() > names.size()) {
                names.add(t("rewards.more", Map.of("count", Integer.toString(milestone.rewards().size() - names.size()))));
            }
            return List.copyOf(names);
        }

        private Component lore(
            final String key,
            final Map<String, String> values,
            final Map<String, List<Component>> sections
        ) {
            Map<String, List<Component>> indented = new LinkedHashMap<>();
            sections.forEach((name, lines) -> indented.put(name, lines.stream()
                .map(line -> line.equals(Component.empty()) ? line : Component.text(" ").append(line)).toList()));
            return Component.join(JoinConfiguration.newlines(), provider.lore(key, values, indented));
        }

        private Component t(final String key) {
            return provider.text(key);
        }

        private Component t(final String key, final Map<String, String> values) {
            return provider.text(key, values);
        }

        private void action(final Map<String, VexUIAction> actions, final String id, final VexUIAction handler) {
            VexUIActions.register(actions, id, handler);
        }

        private void select(final JobState state, final int level) {
            state.selectedLevel = level;
            state.itemPage = 0;
        }

        private Milestone milestone(final Profession job, final int level) {
            return job.milestones().stream().filter(value -> value.level() == level).findFirst().orElse(null);
        }

        private static String plain(final Component component) {
            return PlainTextComponentSerializer.plainText().serialize(component);
        }

        private Map<String, String> values(final Profession job) {
            return Map.of("level", Integer.toString(job.level()), "maximum", Integer.toString(job.maximum()),
                "xp", Long.toString(Math.round(job.experience())), "required", Long.toString(Math.round(job.required())),
                "progress", Long.toString(Math.round(job.progress() * 100)), "available", Integer.toString(job.available()));
        }
    }
}
