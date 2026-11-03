package dev.vexsoft.core.paper.vexui.debug;

import dev.vexsoft.core.paper.vexui.VexUIAction;
import dev.vexsoft.core.paper.vexui.VexUIActionLabels;
import dev.vexsoft.core.paper.vexui.VexUIActionStatus;
import dev.vexsoft.core.paper.vexui.VexUIAsyncAction;
import dev.vexsoft.core.paper.vexui.VexUIButton;
import dev.vexsoft.core.paper.vexui.VexUIButtonRole;
import dev.vexsoft.core.paper.vexui.VexUIClickSound;
import dev.vexsoft.core.paper.vexui.VexUIColumn;
import dev.vexsoft.core.paper.vexui.VexUIConfirmation;
import dev.vexsoft.core.paper.vexui.VexUIDebugOptions;
import dev.vexsoft.core.paper.vexui.VexUIElement;
import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.VexUIItemIcon;
import dev.vexsoft.core.paper.vexui.VexUINavigation;
import dev.vexsoft.core.paper.vexui.VexUIOption;
import dev.vexsoft.core.paper.vexui.VexUIPagedGrid;
import dev.vexsoft.core.paper.vexui.VexUIPanel;
import dev.vexsoft.core.paper.vexui.VexUIProductCard;
import dev.vexsoft.core.paper.vexui.VexUIQuantitySelector;
import dev.vexsoft.core.paper.vexui.VexUIRow;
import dev.vexsoft.core.paper.vexui.VexUISearch;
import dev.vexsoft.core.paper.vexui.VexUISearchBar;
import dev.vexsoft.core.paper.vexui.VexUISelectionGroup;
import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.VexUISize;
import dev.vexsoft.core.paper.vexui.VexUISpacer;
import dev.vexsoft.core.paper.vexui.VexUIState;
import dev.vexsoft.core.paper.vexui.VexUIStyleSheet;
import dev.vexsoft.core.paper.vexui.VexUIText;
import dev.vexsoft.core.paper.vexui.VexUITheme;
import dev.vexsoft.core.paper.vexui.VexUIView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Localized component playground with reactive stock, simulated loading, choices and confirmation navigation. */
public final class VexUIDebugComponentsView implements VexUIView {

    private final int width;
    private final BiFunction<String, Map<String, String>, Component> localize;
    private final Supplier<? extends CompletionStage<Integer>> load;
    private final VexUISelectionGroup tabs;
    private final VexUISelectionGroup preferences;
    private final VexUIState<Integer> stock = new VexUIState<>(8);
    private final VexUIState<String> query = new VexUIState<>("");
    private final VexUIState<String> selected = new VexUIState<>("potato");
    private final VexUIAsyncAction<Integer> loading = new VexUIAsyncAction<>();
    private int quantity = 1;
    private boolean inspect;
    private boolean contrast;

    public VexUIDebugComponentsView(
        final int width,
        final BiFunction<String, Map<String, String>, Component> localize,
        final Supplier<? extends CompletionStage<Integer>> load,
        final boolean inspect
    ) {
        if (width < 400 || width > 700) {
            throw new IllegalArgumentException("Component showcase width must be between 400 and 700");
        }
        this.width = width;
        this.localize = localize;
        this.load = load;
        this.inspect = inspect;
        tabs = new VexUISelectionGroup(List.of(new VexUIOption("catalog", t("tabs.catalog")),
            new VexUIOption("settings", t("tabs.settings"))), false, Set.of("catalog"));
        preferences = new VexUISelectionGroup(List.of(new VexUIOption("sound", t("options.sound")),
            new VexUIOption("notifications", t("options.notifications")),
            new VexUIOption("locked", t("options.locked"), t("options.locked-hint"), false)),
            true, Set.of("sound", "notifications"));
    }

    @Override
    public VexUIFrame render(final VexUISession session) {
        Map<String, VexUIAction> actions = new LinkedHashMap<>();
        var style = session.styleSheet();
        List<VexUIElement> children = new ArrayList<>();
        children.add(tabs.tabs(session, "tab", actions));
        if (session.bind(tabs.selected()).contains("catalog")) {
            catalog(session, children, actions);
        } else {
            settings(session, children, actions);
        }
        actions.put("inspect", viewer -> {
            inspect = !inspect;
            viewer.setDebugOptions(inspect ? VexUIDebugOptions.inspect() : VexUIDebugOptions.disabled());
        });
        children.add(new VexUIRow(style.gap(), VexUINavigation.back(session, nav("back"), actions),
            VexUIButton.text(t(inspect ? "inspect.on" : "inspect.off"), "inspect")));
        var frame = new VexUIFrame(t("title"), nav("close"), width,
            new VexUIColumn(VexUISize.content(), style.gap(), children), actions);
        return session.bind(preferences.selected()).contains("sound") ? frame
            : frame.withClickSound(VexUIClickSound.disabled());
    }

    private void catalog(
        final VexUISession session,
        final List<VexUIElement> children,
        final Map<String, VexUIAction> actions
    ) {
        var style = session.styleSheet();
        String search = session.bind(query);
        String choice = session.bind(selected);
        int available = session.bind(stock);
        var form = new VexUISearch(t("search.title"), t("search.label"), nav("apply"), nav("back"), search,
            value -> query.set(value.trim()));
        children.add(VexUISearchBar.controls("search", session, form, t("search.button", Map.of("query", search)),
            t("search.clear"), !search.isEmpty(), () -> query.set(""), style, actions));
        var entries = List.of("potato", "carrot", "diamond").stream().filter(id -> PlainTextComponentSerializer
            .plainText().serialize(t("items." + id)).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
            .toList();
        children.add(VexUIPagedGrid.slice(entries, 0, 3).grid(3, style.gap(), 36, id -> {
            String action = "select." + id;
            actions.put(action, ignored -> selected.set(id));
            return new VexUIProductCard(new VexUIItemIcon(Key.key("minecraft:item/" + id), 16, Component.empty()),
                t("items." + id), t("card.detail"), t("card.tooltip"))
                .button(action, choice.equals(id), true, 36, style);
        }));
        children.add(new VexUIPanel(VexUISize.content(), style.padding(), style.panel(),
            new VexUIRow(style.gap(), new VexUIItemIcon(Key.key("minecraft:item/" + choice), 32, Component.empty()),
                new VexUIColumn(VexUISize.flexible(), 0, List.of(new VexUIText(t("items." + choice)),
                    new VexUIText(t("stock", Map.of("amount", Integer.toString(available)))))))));
        children.add(new VexUIQuantitySelector(quantity, 1, 64, 16).controls("amount", key -> t("amount." + key),
            value -> t("quantity", Map.of("amount", Integer.toString(value))), value -> quantity = value, style, actions));
        actions.put("stock.add", ignored -> stock.update(value -> Math.min(999, value + 1)));
        actions.put("stock.take", ignored -> stock.update(value -> value < quantity ? value : value - quantity));
        children.add(new VexUIRow(style.gap(), VexUIButton.text(t("stock-add"), "stock.add"),
            new VexUIButton(VexUISize.flexible(), new VexUIText(t(available < quantity ? "stock-empty" : "stock-take")),
                style.primary(), available >= quantity, "stock.take", t("stock-hint"))
                .withRole(VexUIButtonRole.PRIMARY)));
        actions.put("load", viewer -> loading.run(viewer, load,
            (active, value) -> stock.set(value), (active, error) -> { }));
        actions.put("load.error", viewer -> loading.run(viewer,
            () -> CompletableFuture.failedFuture(new IllegalStateException("Requested demonstration failure")),
            (active, value) -> stock.set(value), (active, error) -> { }));
        children.add(new VexUIRow(style.gap(), loading.button(session, "load", new VexUIActionLabels(
            t("load.idle"), t("load.running"), t("load.success"), t("load.error")), t("load.hint")),
            new VexUIButton(VexUISize.flexible(), new VexUIText(t("load.fail")), style.danger(),
                session.bind(loading.status()) != VexUIActionStatus.RUNNING,
                "load.error", t("load.hint")).withRole(VexUIButtonRole.DANGER)));
        children.add(session.bind(preferences.selected()).contains("notifications") ? new VexUIText(t("hint"))
            : new VexUISpacer(new VexUISize(-1, 12, 0)));
    }

    private void settings(
        final VexUISession session,
        final List<VexUIElement> children,
        final Map<String, VexUIAction> actions
    ) {
        children.add(new VexUIText(t("options.title")));
        children.add(preferences.controls(session, "option", 3, actions));
        actions.put("theme", viewer -> {
            contrast = !contrast;
            var base = VexUIStyleSheet.defaults();
            viewer.setStyleSheet(contrast ? new VexUIStyleSheet(
                new VexUITheme(0x352D40, 0x17141F, 0xB69ACB, 0x756182), base.primary(), base.selected(), base.danger(),
                base.disabled(), base.panel(), base.gap(), base.padding(), 22, base.navigationWidth()) : null);
        });
        actions.put("reset", viewer -> viewer.navigate(new VexUIConfirmation(t("confirm.title"), nav("close"),
            new VexUIText(t("confirm.body")), t("confirm.accept"), nav("back"), width, true, () -> {
                stock.set(8);
                quantity = 1;
                loading.reset();
            })));
        children.add(new VexUIRow(session.styleSheet().gap(),
            VexUIButton.text(t(contrast ? "theme.contrast" : "theme.default"), "theme"),
            VexUIButton.text(t("reset"), "reset").withRole(VexUIButtonRole.DANGER)));
        children.add(new VexUIText(t("options.hint")));
    }

    private Component t(final String key) {
        return t(key, Map.of());
    }

    private Component t(final String key, final Map<String, String> replacements) {
        return localize.apply("vexui.components." + key, replacements);
    }

    private Component nav(final String key) {
        return localize.apply("vexui.navigation." + key, Map.of());
    }
}
