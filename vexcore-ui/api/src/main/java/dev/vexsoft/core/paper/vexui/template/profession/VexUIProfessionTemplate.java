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
import dev.vexsoft.core.paper.vexui.element.VexUIEntrance;
import dev.vexsoft.core.paper.vexui.element.VexUIGrid;
import dev.vexsoft.core.paper.vexui.element.VexUIGridCell;
import dev.vexsoft.core.paper.vexui.element.VexUIMap;
import dev.vexsoft.core.paper.vexui.element.VexUIPanel;
import dev.vexsoft.core.paper.vexui.element.VexUIParagraph;
import dev.vexsoft.core.paper.vexui.element.VexUIProgressBar;
import dev.vexsoft.core.paper.vexui.element.VexUIRow;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUISpacer;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.style.VexUIAttention;
import dev.vexsoft.core.paper.vexui.style.VexUIButtonRole;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionScene.Entrance;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Entry;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Milestone;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Profession;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Status;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Profession selection, level paths and viewer-owned milestone reward dialogs. */
public final class VexUIProfessionTemplate {

    /** Creates a fresh workspace owning the supplied viewer adapter. */
    public VexUIView createView(final VexUIProfessionProvider provider) {
        return new View(Objects.requireNonNull(provider, "provider"), null);
    }

    /** Opens the same workspace with the station's profession selected. */
    public VexUIView createView(final VexUIProfessionProvider provider, final String profession) {
        return new View(Objects.requireNonNull(provider, "provider"), profession);
    }

    private record Workspace(VexUIElement content, VexUIElement navigation) {
    }

    private record SceneKey(String job, boolean catalog, int page) {
        boolean turnsPathPage(final SceneKey previous) {
            return previous != null && job.equals(previous.job) && !catalog && !previous.catalog && page != previous.page;
        }
    }

    private record ControlCaption(int iconX, int textX, int textWidth) {
        static ControlCaption centered(final VexUISession session, final int x, final int width, final int iconWidth,
                                       final int gap, final Component label) {
            int textWidth = session.fittedTextWidth(label, width - iconWidth - gap);
            int spacing = textWidth > 0 ? gap : 0;
            // The last glyph's advance includes one trailing pixel between letters.
            int inkWidth = Math.max(0, textWidth - 1);
            int iconX = x + (width - iconWidth - spacing - inkWidth) / 2;
            return new ControlCaption(iconX, iconX + iconWidth + spacing, Math.max(1, textWidth));
        }
    }

    private static final class View implements VexUIView {
        private static final int SCENE_TRANSITION_TICKS = 36;
        private static final int JOB_LABEL_DELAY = 5;
        private final VexUIProfessionProvider provider;
        private final Map<String, Integer> pathPages = new HashMap<>();
        private final VexUIAsyncAction<Boolean> claims = new VexUIAsyncAction<>();
        private String profession;
        private int professionPage;
        private int filter;
        private int sort;
        private boolean locateSelection = true;
        private boolean sceneCatalog;
        private VexUIProfessionRewardPopup popup;
        private int catalogPage;
        private SceneKey sceneKey;
        private Entrance entrance = Entrance.SETTLED;
        private String sceneTheme;
        private String previousTheme;
        private long sceneStart;
        private List<VexUIMap.Layer> lastPath = List.of();
        private List<VexUIMap.Layer> outgoingPath = List.of();
        private int jobStripPage = -1;
        private int previousStripPage;
        private String previousStripJob;
        private boolean openingStrip;
        private boolean showJobLabel;

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
            String theme = job == null ? null : provider.sceneTheme(job.id());
            if (popup != null && (job == null || !popup.belongsTo(job))) {
                popup = null;
            }
            if (theme != null) {
                return scene(session, snapshot, job, theme, running, actions);
            }
            sceneKey = null;
            sceneTheme = null;
            lastPath = List.of();
            if (popup != null) {
                var reward = milestone(job, popup.level());
                registerPopupActions(job, reward, running, actions);
                var content = popup.standardContent(session, provider, reward, running, actions);
                return new VexUIFrame(t("main.title"), t("main.close"), layout.width(), content, actions)
                    .withCompactNativeCloseButton();
            }
            var header = new VexUIRow(4, new VexUISpacer(VexUISize.flexible()),
                VexUINavigation.back(session, t("main.back"), actions));
            var sidebar = sidebar(session, snapshot, actions);
            var workspace = job == null
                ? new Workspace(new VexUIParagraph(t("main.empty")), new VexUISpacer(VexUISize.fixed(1, 1)))
                : job(session, job, actions);
            var content = new VexUIColumn(VexUISize.content(), 4, List.of(header,
                workspaceRow(sidebar.content(), workspace.content()), workspaceRow(sidebar.navigation(), workspace.navigation())));
            return new VexUIFrame(t("main.title"), t("main.close"), layout.width(),
                new VexUIPanel(VexUISize.content(), 6, session.styleSheet().panel(), content), actions);
        }

        @Override
        public void closed() {
            provider.close();
        }

        @Override
        public void closeRequested(final VexUISession session) {
            if (popup != null) {
                popup = null;
                session.refresh();
            } else {
                session.close();
            }
        }

        private VexUIFrame scene(
            final VexUISession session,
            final VexUIProfessionSnapshot snapshot,
            final Profession job,
            final String theme,
            final boolean running,
            final Map<String, VexUIAction> actions
        ) {
            int pages = Math.ceilDiv(job.maximum(), VexUIProfessionScene.LEVELS);
            int initial = (Math.max(1, job.level()) - 1) / VexUIProfessionScene.LEVELS;
            int page = Math.clamp(pathPages.getOrDefault(job.id(), initial), 0, pages - 1);
            var key = new SceneKey(job.id(), sceneCatalog, sceneCatalog ? catalogPage : page);
            long now = session.player().getWorld().getGameTime();
            if (!key.equals(sceneKey)) {
                openingStrip = sceneKey == null;
                previousStripJob = !openingStrip && !job.id().equals(sceneKey.job()) ? sceneKey.job() : null;
                previousStripPage = jobStripPage;
                entrance = key.turnsPathPage(sceneKey) ? Entrance.PATH_PAGE : Entrance.SCENE;
                outgoingPath = sceneKey == null ? List.of() : lastPath;
                previousTheme = theme.equals(sceneTheme) ? null : sceneTheme;
                sceneTheme = theme;
                sceneKey = key;
                sceneStart = now;
                showJobLabel = previousStripJob == null || previousStripPage < 0;
                if (showJobLabel) {
                    session.refreshAfter(SCENE_TRANSITION_TICKS);
                } else {
                    // Reveal the native, localized caption once its tab has expanded to fit it.
                    session.updateAfter(JOB_LABEL_DELAY, () -> {
                        showJobLabel = true;
                        session.refreshAfter(SCENE_TRANSITION_TICKS - JOB_LABEL_DELAY);
                    });
                }
            }
            if (popup != null) {
                // Once a popup settles the path, closing it must not resume an earlier entrance halfway through.
                sceneStart = now - SCENE_TRANSITION_TICKS;
            }
            boolean entering = now >= sceneStart && now - sceneStart < SCENE_TRANSITION_TICKS;
            var map = new VexUIProfessionScene(theme, sceneStart, entering ? entrance : Entrance.SETTLED);
            if (entering) {
                map.outgoingPath(outgoingPath);
            } else {
                outgoingPath = List.of();
            }
            sceneHeader(session, map, theme, job, actions);
            if (sceneCatalog) {
                map.detailSurface();
            } else {
                pathNavigation(map, job.id(), page, pages, actions);
            }
            int start = map.layerCount();
            if (sceneCatalog) {
                catalog(map, snapshot, actions);
            } else {
                path(map, job, page, actions);
            }
            lastPath = map.pathSprites(start);
            jobStrip(session, map, snapshot.professions(), job, entering, actions);
            if (popup != null) {
                map.freezeBackdrop();
                actions.clear();
                var reward = milestone(job, popup.level());
                registerPopupActions(job, reward, running, actions);
                popup.render(map, session, provider, reward, running, actions);
            }
            var title = map.background(previousTheme, provider.hasPanoramaSprites());
            return new VexUIFrame(title, t("main.close"), VexUIProfessionScene.WIDTH, map.content(), actions)
                .withCompactNativeCloseButton();
        }

        private void sceneHeader(final VexUISession session, final VexUIProfessionScene map, final String theme,
                                 final Profession job, final Map<String, VexUIAction> actions) {
            map.icon(0, 0, theme, 0);
            map.text(21, 0, 150, job.name());
            map.text(21, 10, 166, t("main.progress", values(job)));
            map.add(21, 22, 154, 3, new VexUIProgressBar(VexUISize.fixed(154, 3), job.progress(), 0,
                0xE8BB69, 0x403748));
            var next = job.level() == job.maximum() ? t("job.maximum", values(job))
                : t("job.scene_remaining", Map.of("remaining", Long.toString(Math.max(0,
                    (long) Math.ceil(job.required() - job.experience()))), "next", Integer.toString(job.level() + 1)));
            map.text(203, 0, 199, next);
            var catalog = t("main.scene_catalog");
            action(actions, "scene.catalog", ignored -> sceneCatalog = true);
            map.art(201, 18, 0xE02C, 16, 16, 1);
            map.text(219, 22, 60, sceneCatalog ? catalog.decorate(TextDecoration.UNDERLINED) : catalog);
            map.hotspot(201, 18, 78, 18, "scene.catalog", t("main.title"));

            var back = t("main.back");
            String backAction;
            if (sceneCatalog) {
                backAction = "scene.return";
                action(actions, backAction, ignored -> sceneCatalog = false);
            } else {
                backAction = VexUINavigation.back(session, back, actions).action();
            }
            int backWidth = 60;
            int backX = VexUIProfessionScene.WIDTH - backWidth;
            var caption = ControlCaption.centered(session, backX, backWidth, 10, 2, back);
            map.settledArt(caption.iconX(), 21, 0xE004, 10, 12);
            map.text(caption.textX(), 21, caption.textWidth(), back);
            map.hotspot(backX, 18, backWidth, 18, backAction, back);
        }

        private void pathNavigation(final VexUIProfessionScene map, final String job, final int page,
                                    final int pages, final Map<String, VexUIAction> actions) {
            action(actions, "page.previous", ignored -> pathPages.put(job, Math.max(0, page - 1)));
            action(actions, "page.next", ignored -> pathPages.put(job, Math.min(pages - 1, page + 1)));
            map.arrow(false, page > 0, t("main.previous"));
            map.arrow(true, page + 1 < pages, t("main.next"));
        }

        private void path(final VexUIProfessionScene map, final Profession job, final int page,
                          final Map<String, VexUIAction> actions) {
            int first = page * VexUIProfessionScene.LEVELS + 1;
            int count = Math.min(job.maximum() - first + 1, VexUIProfessionScene.LEVELS);
            for (int slot = 0; slot + 1 < count; slot++) {
                map.bridge(slot);
            }
            for (int slot = 0; slot < count; slot++) {
                int level = first + slot;
                var milestone = milestone(job, level);
                var status = milestone == null ? level <= job.level() ? Status.REACHED : Status.LOCKED : milestone.status();
                String id = "level." + level;
                action(actions, id, ignored -> openLevel(job, page, level));
                var rewards = milestone == null ? List.of(t("levels.no_reward")) : rewardNames(milestone);
                var hover = lore("levels.milestone_lore", Map.of("level", Integer.toString(level)), Map.of("rewards", rewards))
                    .append(Component.newline()).append(t("levels." + status.name().toLowerCase(Locale.ROOT)));
                map.island(slot, level, job.level(), status, milestone != null, id, hover);
            }
        }

        private void openLevel(final Profession job, final int page, final int level) {
            pathPages.put(job.id(), page);
            popup = new VexUIProfessionRewardPopup(job.id(), level);
        }

        private void registerPopupActions(final Profession job, final Milestone reward, final boolean running,
                                          final Map<String, VexUIAction> actions) {
            action(actions, "popup.close", ignored -> popup = null);
            var selected = popup;
            if (!running && selected.canClaim(reward)) {
                action(actions, "claim.level." + selected.level(), viewer -> {
                    selected.retry();
                    claims.run(viewer, () -> provider.claim(job.id(), selected.level()), (active, collected) -> {
                        if (Boolean.TRUE.equals(collected)) {
                            selected.collected();
                            active.player().playSound(VexUIClickSound.collect().asSound());
                        } else {
                            selected.failed();
                        }
                    }, (active, failure) -> selected.failed());
                });
            }
        }

        private void catalog(final VexUIProfessionScene map, final VexUIProfessionSnapshot snapshot,
                             final Map<String, VexUIAction> actions) {
            var page = VexUIPagedGrid.slice(sortedJobs(snapshot), catalogPage, 3);
            catalogPage = page.page();
            map.art(31, 54, 0xE027, 54, 54, 1);
            map.centeredText(18, 112, 82, t("main.title"));
            for (int i = 0; i < page.entries().size(); i++) {
                var value = page.entries().get(i);
                int x = 104 + i * 95;
                var label = values(value);
                boolean active = value.id().equals(profession);
                map.art(x, 54, active ? 0xE031 : 0xE030, 88, 45, i + 2);
                String asset = provider.sceneTheme(value.id());
                if (asset == null) {
                    map.add(x + 36, 62, 16, 16, value.icon().at(16));
                } else {
                    map.icon(x + 36, 63, asset, i + 2);
                }
                map.centeredText(x + 2, 86, 84, value.name());
                map.centeredText(x + 2, 104, 84, t("main.progress", label));
                String id = "catalog." + value.id();
                action(actions, id, ignored -> {
                    profession = value.id();
                    jobStripPage = -1;
                    sceneCatalog = false;
                });
                map.hotspot(x, 54, 88, 54, id, lore("main.profession_lore", label,
                    Map.of("name", List.of(value.name()), "description", value.description())));
            }
            if (page.entries().isEmpty()) {
                map.text(107, 72, 270, t("main.empty"));
            }
            action(actions, "filter", ignored -> {
                filter = 1 - filter;
                catalogPage = 0;
            });
            action(actions, "sort", ignored -> {
                sort = (sort + 1) % 3;
                catalogPage = 0;
            });
            map.text(107, 130, 85, t("main.filter_" + filter));
            map.hotspot(104, 126, 89, 18, "filter", t("main.availability_filter_hint"));
            map.text(204, 130, 85, t("main.sort_" + sort));
            map.hotspot(199, 126, 89, 18, "sort", t("main.sort_hint"));
            map.pager(provider, actions, "catalog", 294, 126, 87, page, value -> catalogPage = value);
        }

        private List<Profession> sortedJobs(final VexUIProfessionSnapshot snapshot) {
            Comparator<Profession> order = sort == 1 ? Comparator.comparing(value -> plain(value.name()))
                : sort == 2 ? Comparator.comparingInt(Profession::level).reversed() : null;
            var filtered = snapshot.professions().stream().filter(value -> filter == 0 || value.available() > 0);
            return order == null ? filtered.toList() : filtered.sorted(order).toList();
        }

        private void jobStrip(final VexUISession session, final VexUIProfessionScene map, final List<Profession> all,
                              final Profession job, final boolean entering, final Map<String, VexUIAction> actions) {
            int pages = Math.ceilDiv(all.size(), VexUIProfessionScene.JOBS);
            if (jobStripPage < 0) {
                jobStripPage = all.indexOf(job) / VexUIProfessionScene.JOBS;
            }
            jobStripPage = Math.clamp(jobStripPage, 0, pages - 1);
            int start = jobStripPage * VexUIProfessionScene.JOBS;
            var visible = all.subList(start, Math.min(start + VexUIProfessionScene.JOBS, all.size()));
            boolean switching = entering && previousStripJob != null && previousStripPage == jobStripPage;
            boolean hadSelection = switching && visible.stream().anyMatch(value -> value.id().equals(previousStripJob));
            int x = jobStripStart(visible.size(), visible.contains(job));
            int previousX = jobStripStart(visible.size(), hadSelection);
            int slot = 0;
            for (var choice : visible) {
                boolean active = choice.id().equals(job.id());
                int cell = VexUIProfessionScene.jobWidth(active);
                String id = "profession." + choice.id();
                action(actions, id, ignored -> {
                    profession = choice.id();
                    sceneCatalog = false;
                });
                var hint = lore("main.profession_lore", values(choice),
                    Map.of("name", List.of(choice.name()), "description", choice.description()));
                boolean wasActive = switching && choice.id().equals(previousStripJob);
                int previousWidth = VexUIProfessionScene.jobWidth(wasActive);
                var caption = jobCaption(session, choice, x, active);
                var baseTint = openingStrip && entering
                    ? VexUIEntrance.color(sceneStart, 2 + slot) : VexUIEntrance.SETTLED;
                TextColor buttonTint = baseTint;
                TextColor iconTint = baseTint;
                if (switching) {
                    int oldIconX = jobCaption(session, choice, previousX, wasActive).iconX();
                    buttonTint = tabTint(previousX - x, previousWidth - cell);
                    iconTint = tabTint(oldIconX - caption.iconX(), 0);
                }
                map.jobButton(x, active, buttonTint);
                if (active && (!switching || showJobLabel)) {
                    map.text(caption.textX(), 177, caption.textWidth(), choice.name());
                }
                String asset = provider.sceneTheme(choice.id());
                if (asset == null) {
                    map.add(caption.iconX(), 175, 16, 16, choice.icon().at(16));
                } else {
                    map.jobIcon(caption.iconX(), asset, iconTint);
                }
                map.hotspot(x, 171, cell, 27, id, hint);
                x += cell + 3;
                previousX += previousWidth + 3;
                slot++;
            }
            if (pages > 1) {
                int current = jobStripPage;
                action(actions, "professions.previous", ignored -> jobStripPage = Math.max(0, current - 1));
                action(actions, "professions.next", ignored -> jobStripPage = Math.min(pages - 1, current + 1));
                map.add(3, 171, 24, 24, button(session, t("main.previous"), "professions.previous", current > 0, 24));
                map.add(VexUIProfessionScene.WIDTH - 27, 171, 24, 24, button(session, t("main.next"),
                    "professions.next", current + 1 < pages, 24));
            }
        }

        private static int jobStripStart(final int count, final boolean selectionVisible) {
            int compact = VexUIProfessionScene.jobWidth(false);
            int width = (count - 1) * (compact + 3) + VexUIProfessionScene.jobWidth(selectionVisible);
            return (VexUIProfessionScene.WIDTH - width) / 2;
        }

        private static ControlCaption jobCaption(final VexUISession session, final Profession choice, final int x,
                                                 final boolean active) {
            return active
                ? ControlCaption.centered(session, x + 3, VexUIProfessionScene.jobWidth(true) - 6, 16, 3, choice.name())
                : new ControlCaption(x + 4, 0, 0);
        }

        private TextColor tabTint(final int startOffset, final int widthDelta) {
            return startOffset == 0 && widthDelta == 0 ? VexUIEntrance.SETTLED
                : VexUIEntrance.jobTabColor(sceneStart, startOffset, widthDelta);
        }

        private Workspace sidebar(
            final VexUISession session,
            final VexUIProfessionSnapshot snapshot,
            final Map<String, VexUIAction> actions
        ) {
            action(actions, "filter", ignored -> {
                filter = 1 - filter;
                professionPage = 0;
            });
            action(actions, "sort", ignored -> {
                sort = (sort + 1) % 3;
                professionPage = 0;
            });
            var filtered = sortedJobs(snapshot);
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
                button(session, t("main.filter_" + filter), "filter", true, -1),
                button(session, t("main.sort_" + sort), "sort", true, -1)));
            var grid = current.grid(1, 2, layout.cellHeight(), value -> {
                String id = "profession." + value.id();
                action(actions, id, ignored -> {
                    profession = value.id();
                    sceneCatalog = false;
                    jobStripPage = -1;
                });
                var line = new VexUISize(-1, 9, 0);
                var label = new VexUIRow(4, value.icon().at(16), new VexUIColumn(VexUISize.flexible(), 0,
                    List.of(new VexUIText(line, value.name(), false),
                        new VexUIText(line, t("main.progress", values(value)), false))));
                return new VexUIButton(new VexUISize(-1, layout.cellHeight(), 0), label, session.styleSheet().normal(),
                    true, id, lore("main.profession_lore", values(value), Map.of("name", List.of(value.name()),
                        "description", value.description()))).withRole(value.id().equals(profession)
                            ? VexUIButtonRole.SELECTED : VexUIButtonRole.NORMAL)
                    .withAttention(new VexUIAttention(value.available(), false));
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

        private Workspace job(final VexUISession session, final Profession job, final Map<String, VexUIAction> actions) {
            var heading = new VexUIColumn(VexUISize.flexible(), 2, List.of(new VexUIText(job.name()),
                new VexUIText(t(job.level() == job.maximum() ? "job.maximum" : "job.progress", values(job))),
                new VexUIProgressBar(new VexUISize(-1, 5, 0), job.progress(), 0,
                    session.styleSheet().selected().lightBorder(), session.styleSheet().normal().background())));
            var summary = new VexUIPanel(VexUISize.content(), 4, session.styleSheet().normal(),
                    new VexUIRow(6, job.icon().at(32), heading))
                .withTooltip(lore("main.profession_lore", values(job),
                    Map.of("name", List.of(job.name()), "description", job.description())));
            var section = levels(session, job, actions);
            return new Workspace(new VexUIColumn(VexUISize.content(), 4, List.of(summary, section.content())),
                section.navigation());
        }

        private Workspace levels(
            final VexUISession session,
            final Profession job,
            final Map<String, VexUIAction> actions
        ) {
            var layout = provider.layout("levels");
            int pages = Math.max(1, Math.ceilDiv(job.maximum(), layout.capacity()));
            int firstAvailable = job.milestones().stream().filter(value -> value.status() == Status.AVAILABLE)
                .mapToInt(Milestone::level).min().orElse(Math.min(job.maximum(), job.level() + 1));
            int page = Math.clamp(pathPages.getOrDefault(job.id(), (firstAvailable - 1) / layout.capacity()), 0, pages - 1);
            var numbers = IntStream.rangeClosed(page * layout.capacity() + 1,
                Math.min(job.maximum(), (page + 1) * layout.capacity())).boxed().toList();
            var current = new VexUIPagedGrid<>(numbers, page, pages, job.maximum(), layout.capacity());
            var grid = current.grid(1, 2, layout.cellHeight(), level -> {
                var milestone = milestone(job, level);
                var status = milestone == null ? level <= job.level() ? Status.REACHED : Status.LOCKED : milestone.status();
                String id = "level." + level;
                action(actions, id, ignored -> openLevel(job, page, level));
                var label = new VexUIRow(3, new VexUIText(new VexUISize(43, -1, 0),
                    t("levels.level", Map.of("level", level.toString())), false), new VexUIText(VexUISize.flexible(),
                    t("levels." + status.name().toLowerCase(Locale.ROOT)), false));
                var rewards = milestone == null ? List.of(t("levels.no_reward")) : rewardNames(milestone);
                return new VexUIButton(new VexUISize(-1, layout.cellHeight(), 0), label, session.styleSheet().normal(),
                    true, id, lore("levels.milestone_lore", Map.of("level", level.toString()), Map.of("rewards", rewards)))
                    .withAttention(new VexUIAttention(status == Status.AVAILABLE ? 1 : 0, false));
            });
            return new Workspace(grid, pager(session, "page", current, actions, value -> pathPages.put(job.id(), value)));
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
                button(session, t("main.previous"), prefix + ".previous", current.page() > 0, 24),
                new VexUIText(VexUISize.flexible(), t("main.page", Map.of("page", Integer.toString(current.page() + 1),
                    "pages", Integer.toString(current.pages()))), true),
                button(session, t("main.next"), prefix + ".next", current.page() + 1 < current.pages(), 24));
        }

        private VexUIButton button(
            final VexUISession session,
            final Component label,
            final String id,
            final boolean enabled,
            final int width
        ) {
            String hint = id.equals("filter") ? "main.availability_filter_hint"
                : id.equals("sort") ? "main.sort_hint" : "main.page_hint";
            var text = new VexUIText(new VexUISize(-1, 9, 0), label, true);
            return new VexUIButton(new VexUISize(width, 22, width < 0 ? 1 : 0), text,
                session.styleSheet().normal(), enabled, id, lore("main.control_lore", Map.of(),
                    Map.of("name", List.of(label), "details", List.of(t(hint))))).withRole(VexUIButtonRole.NORMAL);
        }

        private VexUIElement workspaceRow(final VexUIElement sidebar, final VexUIElement workspace) {
            return new VexUIGrid(VexUISize.content(), 4, 8, List.of(new VexUIGridCell(1, 1, sidebar),
                new VexUIGridCell(3, 1, workspace)));
        }

        private int workspaceHeight() {
            return Math.max(22 + 3 + gridHeight("main", 2), 41 + 4 + gridHeight("levels", 2));
        }

        private int gridHeight(final String section, final int gap) {
            var layout = provider.layout(section);
            return layout.capacity() * layout.cellHeight() + Math.max(0, layout.capacity() - 1) * gap;
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
