package dev.vexsoft.core.paper.vexui.template.profession;

import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.action.VexUIActions;
import dev.vexsoft.core.paper.vexui.component.VexUIPagedGrid;
import dev.vexsoft.core.paper.vexui.component.VexUIPagination;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIColumn;
import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import dev.vexsoft.core.paper.vexui.element.VexUIPanel;
import dev.vexsoft.core.paper.vexui.element.VexUIProgressBar;
import dev.vexsoft.core.paper.vexui.element.VexUIRow;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUISpacer;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.style.VexUITheme;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Entry;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Milestone;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Profession;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

/** Viewer-local reward list for the island that the player clicked. */
final class VexUIProfessionRewardPopup {
    private static final int X = VexUIProfessionScene.POPUP_X;
    private static final int WIDTH = VexUIProfessionScene.POPUP_WIDTH;
    private static final int ROWS = 3;
    private static final VexUITheme FRAME = new VexUITheme(0x222429, 0x452C24, 0xBC825C, 0x87593F);
    private static final VexUITheme GOLD_BUTTON = new VexUITheme(0xE6AD4D, 0x63442B, 0xFFDC83, 0xA97132);
    private static final int RULE = 0x514E52;
    private final String profession;
    private final int level;
    private int page;
    private boolean collected;
    private boolean failed;

    VexUIProfessionRewardPopup(final String profession, final int level) {
        this.profession = profession;
        this.level = level;
    }

    int level() {
        return level;
    }

    boolean belongsTo(final Profession job) {
        return profession.equals(job.id()) && level <= job.maximum();
    }

    boolean canClaim(final Milestone reward) {
        return reward != null && !reward.rewards().isEmpty() && reward.status() == Status.AVAILABLE && !collected;
    }

    void retry() {
        failed = false;
    }

    void failed() {
        failed = true;
    }

    void collected() {
        // The grant can complete before the next progression snapshot is published.
        collected = true;
    }

    void render(
        final VexUIProfessionScene map,
        final VexUISession session,
        final VexUIProfessionProvider provider,
        final Milestone reward,
        final boolean running,
        final Map<String, VexUIAction> actions
    ) {
        var visible = rewardPage(reward);
        // Reserve all rows on the final page so neither the close nor claim target moves when paging.
        int rows = Math.clamp(visible.total(), 1, ROWS);
        int top = 63 - 9 * rows;
        int height = 72 + 18 * rows;
        surface(map, X, top, WIDTH, height, FRAME);
        separator(map, top + 23);
        int right = X + WIDTH;
        var title = provider.text("rewards.popup_title", Map.of("level", Integer.toString(level)));
        int pagerWidth = 78;
        int controlsX = visible.pages() > 1 ? right - 28 - pagerWidth : right - 22;
        map.centeredText(X + 4, top + 12, controlsX - X - 8, title.hoverEvent(title));
        map.pager(provider, actions, "popup", controlsX, top + 9, pagerWidth, visible, value -> page = value);
        var closeHint = provider.text("main.close");
        map.centeredText(right - 22, top + 12, 18, provider.text("rewards.popup_close"));
        map.hotspot(right - 22, top + 9, 18, 18, "popup.close", closeHint);

        if (visible.entries().isEmpty()) {
            map.centeredText(X + 11, top + 31, WIDTH - 22, provider.text("levels.no_reward"));
        }
        for (int slot = 0; slot < visible.entries().size(); slot++) {
            var entry = visible.entries().get(slot);
            var details = entry.details().stream().filter(line -> !line.equals(entry.name())).toList();
            var hint = rewardHint(provider, entry, details);
            row(map, session, entry.name(), details, hint, top + 29 + slot * 21);
        }

        boolean available = canClaim(reward);
        int buttonY = top + height - 27;
        separator(map, buttonY - (failed ? 2 : 5));
        if (failed) {
            var failure = provider.text("main.failed");
            map.centeredText(X + 5, buttonY - 12, WIDTH - 10, failure.hoverEvent(failure));
        }
        var label = provider.text(claimLabel(reward, running));
        var skin = available && !running ? GOLD_BUTTON : session.styleSheet().disabled();
        int buttonWidth = 100;
        int buttonX = X + (WIDTH - buttonWidth) / 2;
        surface(map, buttonX, buttonY, buttonWidth, 18, skin);
        map.centeredText(buttonX + 2, buttonY + 4, buttonWidth - 4, label);
        if (available && !running) {
            map.hotspot(buttonX, buttonY, buttonWidth, 18, "claim.level." + level, provider.text("rewards.collect_level"));
        }
    }

    VexUIElement standardContent(final VexUISession session, final VexUIProfessionProvider provider,
                                 final Milestone reward, final boolean running, final Map<String, VexUIAction> actions) {
        var visible = rewardPage(reward);
        var theme = session.styleSheet().normal();
        List<VexUIElement> content = new ArrayList<>();
        var title = provider.text("rewards.popup_title", Map.of("level", Integer.toString(level)));
        content.add(new VexUIRow(4, new VexUIText(VexUISize.flexible(), title, false),
            new VexUIButton(VexUISize.fixed(24, 22), new VexUIText(provider.text("rewards.popup_close")), theme,
                true, "popup.close", provider.text("main.close"))));
        if (visible.entries().isEmpty()) {
            content.add(new VexUIText(provider.text("levels.no_reward")));
        } else {
            content.add(visible.grid(1, 3, 24, entry -> {
                var row = new VexUIRow(4, entry.icon().at(16), new VexUIText(entry.name()));
                var details = entry.details().stream().filter(line -> !line.equals(entry.name())).toList();
                return new VexUIPanel(new VexUISize(-1, 24, 0), 3, theme, row)
                    .withTooltip(rewardHint(provider, entry, details));
            }));
        }
        if (visible.pages() > 1) {
            if (page > 0) {
                VexUIActions.register(actions, "popup.previous", ignored -> page = visible.page() - 1);
            }
            if (page + 1 < visible.pages()) {
                VexUIActions.register(actions, "popup.next", ignored -> page = visible.page() + 1);
            }
            var counter = provider.text("main.page", Map.of("page", Integer.toString(page + 1),
                "pages", Integer.toString(visible.pages())));
            content.add(VexUIPagination.compact(visible, provider.text("main.previous"), counter,
                provider.text("main.next"), "popup", theme));
        }
        if (failed) {
            content.add(new VexUIText(provider.text("main.failed")));
        }
        var label = provider.text(claimLabel(reward, running));
        content.add(new VexUIButton(new VexUISize(-1, 22, 0), new VexUIText(label), theme,
            canClaim(reward) && !running, "claim.level." + level, provider.text("rewards.collect_level")));
        return new VexUIPanel(VexUISize.content(), 5, session.styleSheet().panel(),
            new VexUIColumn(VexUISize.content(), 4, content));
    }

    private VexUIPagedGrid<Entry> rewardPage(final Milestone reward) {
        var visible = VexUIPagedGrid.slice(reward == null ? List.<Entry>of() : reward.rewards(), page, ROWS);
        page = visible.page();
        return visible;
    }

    private String claimLabel(final Milestone reward, final boolean running) {
        if (canClaim(reward)) {
            return running ? "rewards.popup_pending" : "rewards.popup_collect";
        }
        if (collected || reward != null && reward.status() == Status.CLAIMED) {
            return "rewards.claimed_button";
        }
        return reward != null && reward.status() == Status.LOCKED ? "rewards.locked_button" : "rewards.collect_level";
    }

    private static Component rewardHint(final VexUIProfessionProvider provider, final Entry entry,
                                        final List<Component> details) {
        return Component.join(JoinConfiguration.newlines(), provider.lore("rewards.item_lore", Map.of(),
            Map.of("name", List.of(entry.name()), "details", details)));
    }

    private static void row(final VexUIProfessionScene map, final VexUISession session, final Component name,
                            final List<Component> details, final Component hint, final int y) {
        int x = X + 13;
        int width = WIDTH - 26;
        int detailWidth = 74;
        int nameWidth = width - detailWidth - 6;
        if (details.isEmpty()) {
            map.centeredText(x, y + 5, width, name.hoverEvent(hint));
            return;
        }
        if (details.size() == 1 && session.wrap(name, nameWidth).size() == 1
            && session.wrap(details.getFirst(), detailWidth).size() == 1) {
            map.centeredText(x, y + 5, nameWidth, name.hoverEvent(hint));
            map.centeredText(x + width - detailWidth, y + 5, detailWidth, details.getFirst().hoverEvent(hint));
        } else {
            map.centeredText(x, y, width, name.hoverEvent(hint));
            map.centeredText(x, y + 10, width, details.getFirst().hoverEvent(hint));
        }
    }

    private static void separator(final VexUIProfessionScene map, final int y) {
        var size = VexUISize.fixed(WIDTH - 18, 1);
        map.add(X + 9, y, size.width(), 1, new VexUIProgressBar(size, 0, 0, RULE, RULE));
    }

    private static void surface(final VexUIProfessionScene map, final int x, final int y,
                                final int width, final int height, final VexUITheme skin) {
        map.add(x, y, width, height, new VexUIPanel(VexUISize.fixed(width, height), 3, skin,
            new VexUISpacer(VexUISize.fixed(1, 1))));
    }
}
