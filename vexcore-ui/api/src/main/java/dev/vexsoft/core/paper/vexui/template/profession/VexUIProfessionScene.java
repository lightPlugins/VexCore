package dev.vexsoft.core.paper.vexui.template.profession;

import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.action.VexUIActions;
import dev.vexsoft.core.paper.vexui.component.VexUIPagedGrid;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import dev.vexsoft.core.paper.vexui.element.VexUIEntrance;
import dev.vexsoft.core.paper.vexui.element.VexUIHotspot;
import dev.vexsoft.core.paper.vexui.element.VexUIMap;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.element.VexUITexture;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;

/** Paints the native profession map. Decorative glyphs never determine its widget or hit geometry. */
final class VexUIProfessionScene {
    static final int WIDTH = 404;
    static final int HEIGHT = 198;
    static final int LEVELS = 5;
    static final int JOBS = 7;
    static final int POPUP_X = 86;
    static final int POPUP_WIDTH = 232;
    private static final Key VANILLA_FONT = Key.key("minecraft:default");
    private static final Key GUI_ATLAS = Key.key("minecraft:gui");
    private static final TextColor PANORAMA_UNDERLAY = TextColor.color(0xFFFFFC);
    // Each vanilla space is four pixels wide. Four row rewinds in the loaded panorama font cancel this advance.
    private static final Component PANORAMA_LEAD = Component.text(" ".repeat(1008)).font(VANILLA_FONT);
    private static final String PANORAMA_ORIGIN = "\uE10D".repeat(4);
    private static final Component TITLE_PADDING = Component.text(" ".repeat(512)).font(VANILLA_FONT);

    enum Entrance { SETTLED, PATH_PAGE, SCENE }

    private final List<VexUIMap.Layer> layers = new ArrayList<>();
    private final Key theme;
    private final Key shared;
    private final long started;
    private final Entrance entrance;

    VexUIProfessionScene(final String theme, final long started, final Entrance entrance) {
        this.theme = Key.key(theme);
        int separator = this.theme.value().lastIndexOf('/');
        this.shared = Key.key(this.theme.namespace(), this.theme.value().substring(0, separator + 1) + "shared");
        this.started = started;
        this.entrance = entrance;
    }

    Component background(final String previousTheme, final boolean hasPanoramaSprites) {
        var title = Component.text().append(PANORAMA_LEAD);
        boolean crossfade = entrance == Entrance.SCENE && previousTheme != null;
        if (crossfade && !hasPanoramaSprites) {
            // The shader centers the outgoing tiles; their native position is outside the viewport.
            title.append(panorama(backgroundFont(Key.key(previousTheme)), VexUIEntrance.exitColor(started, 0)));
        }
        var font = backgroundFont(theme);
        // Only this font can rewind to the visible origin. Missing-glyph boxes stay beyond the right edge.
        title.append(Component.text(PANORAMA_ORIGIN).font(font));
        var currentTint = crossfade ? ink(0) : VexUIEntrance.SETTLED;
        if (hasPanoramaSprites) {
            // The bitmap also works with an older pack. Put native 8px objects outside the viewport
            // so a client using a different GUI text shader sees only the full-size bitmap.
            title.append(panorama(font, PANORAMA_UNDERLAY)).append(PANORAMA_LEAD);
            if (crossfade) {
                title.append(panoramaSprites(Key.key(previousTheme), VexUIEntrance.exitColor(started, 0)));
            }
            return title.append(panoramaSprites(theme, currentTint)).build();
        }
        title.append(panorama(font, currentTint));
        // The native header expands from screen x=0. Its out-of-bounds warning button is then relocated
        // by DialogScreen to the top-right corner, retaining its actual clickable help screen.
        return title.append(TITLE_PADDING).build();
    }

    void add(final int x, final int y, final int width, final int height, final VexUIElement element) {
        layers.add(new VexUIMap.Layer(x, y, width, height, element));
    }

    void text(final int x, final int y, final int width, final Component value) {
        add(x, y, width, 9, new VexUIText(VexUISize.fixed(width, 9), value, false));
    }

    void icon(final int x, final int y, final String asset, final int delay) {
        sprite(Key.key(asset), 0xE005, x, y, 16, 16, ink(delay));
    }

    void jobButton(final int x, final boolean active, final TextColor tint) {
        sprite(shared, active ? 0xE009 : 0xE008, x, 171, jobWidth(active), 24, tint);
    }

    static int jobWidth(final boolean active) {
        return active ? 86 : 24;
    }

    void jobIcon(final int x, final String asset, final TextColor tint) {
        sprite(Key.key(asset), 0xE005, x, 175, 16, 16, tint);
    }

    void art(final int x, final int y, final int glyph, final int width, final int height, final int delay) {
        sprite(shared, glyph, x, y, width, height, ink(delay));
    }

    void settledArt(final int x, final int y, final int glyph, final int width, final int height) {
        sprite(shared, glyph, x, y, width, height, VexUIEntrance.SETTLED);
    }

    void centeredText(final int x, final int y, final int width, final Component value) {
        add(x, y, width, 9, new VexUIText(VexUISize.fixed(width, 9), value, true));
    }

    void detailSurface() {
        for (int piece = 0; piece < 3; piece++) {
            // The parchment, labels and buttons keep their resting native coordinates as the
            // illustrations enter. Disabling the GUI shader still produces the complete layout.
            settledArt(13 + piece * 126, 36, 0xE032 + piece, 126, 117);
        }
    }

    void pager(final VexUIProfessionProvider provider, final Map<String, VexUIAction> actions, final String id,
               final int x, final int y, final int width, final VexUIPagedGrid<?> page, final IntConsumer turn) {
        if (page.pages() <= 1) {
            return;
        }
        centeredText(x + 17, y + 2, width - 34, provider.text("main.page",
            Map.of("page", Integer.toString(page.page() + 1), "pages", Integer.toString(page.pages()))));
        if (page.page() > 0) {
            pageArrow(provider, actions, id + ".previous", x, y, false, () -> turn.accept(page.page() - 1));
        }
        if (page.page() + 1 < page.pages()) {
            pageArrow(provider, actions, id + ".next", x + width - 16, y, true, () -> turn.accept(page.page() + 1));
        }
    }

    private void pageArrow(final VexUIProfessionProvider provider, final Map<String, VexUIAction> actions,
                   final String action, final int x, final int y, final boolean next, final Runnable run) {
        VexUIActions.register(actions, action, ignored -> run.run());
        art(x + 3, y, next ? 0xE005 : 0xE004, 10, 12, 0);
        hotspot(x, y, 16, 18, action, provider.text(next ? "main.next" : "main.previous"));
    }

    int layerCount() {
        return layers.size();
    }

    List<VexUIMap.Layer> pathSprites(final int first) {
        return layers.subList(first, layers.size()).stream()
            .filter(layer -> layer.element() instanceof VexUITexture).toList();
    }

    void outgoingPath(final List<VexUIMap.Layer> departing) {
        for (var layer : departing) {
            var sprite = (VexUITexture) layer.element();
            add(layer.x(), layer.y(), layer.width(), layer.height(), sprite.outgoing(VexUIEntrance.exitColor(started, 0)));
        }
    }

    void bridge(final int slot) {
        int x = islandX(slot) + 41;
        int y = Math.min(islandY(slot), islandY(slot + 1)) + 27;
        boolean rises = islandY(slot + 1) < islandY(slot);
        sprite(shared, rises ? 0xE006 : 0xE007, x, y, 44, 21, pathInk(slot * 2 + 1));
    }

    void island(final int slot, final int level, final int current, final Status status, final boolean hasReward,
                final String action, final Component tooltip) {
        int x = islandX(slot);
        int y = islandY(slot);
        int stage = (level - 1) % LEVELS;
        var tint = pathInk(slot * 2);
        sprite(theme, 0xE000 + stage, x, y, 52, 48, tint);
        if (hasReward) {
            sprite(shared, 0xE028, x + 37, y + 27, 16, 16, tint);
        }
        String digits = Integer.toString(level);
        int labelX = x + (52 - (9 + 11 + digits.length() * 5)) / 2;
        int labelY = y + 45;
        int marker = level == current || status == Status.AVAILABLE ? 0xE003
            : status == Status.LOCKED ? 0xE001 : 0xE002;
        sprite(shared, marker, labelX, labelY, 8, marker == 0xE001 ? 9 : 8, tint);
        sprite(shared, 0xE01A, labelX + 9, labelY, 10, 7, tint);
        for (int index = 0; index < digits.length(); index++) {
            sprite(shared, 0xE010 + digits.charAt(index) - '0', labelX + 20 + 5 * index, labelY, 4, 7, tint);
        }
        hotspot(x, y, 54, 54, action, tooltip);
    }

    void arrow(final boolean next, final boolean available, final Component tooltip) {
        int x = next ? WIDTH - 15 : 4;
        if (available) {
            settledArt(x, 81, next ? 0xE005 : 0xE004, 10, 12);
            hotspot(x - 4, 72, 18, 27, next ? "page.next" : "page.previous", tooltip);
        }
    }

    VexUIMap content() {
        return new VexUIMap(VexUISize.fixed(WIDTH, HEIGHT), layers);
    }

    void freezeBackdrop() {
        // Native font rows paint from top to bottom. Sprites extending below a later panel's top
        // would otherwise draw on its surface. Only the two outer bridges cross its left/right edges.
        int right = POPUP_X + POPUP_WIDTH;
        var iterator = layers.listIterator();
        while (iterator.hasNext()) {
            var layer = iterator.next();
            if (layer.element() instanceof VexUIHotspot) {
                iterator.remove();
            } else if (layer.element() instanceof VexUIButton button) {
                var disabled = new VexUIButton(button.size(), button.content(), button.theme(), false, button.action(),
                    Component.empty(), button.clickSound(), button.role(), button.attention(), button.padding());
                iterator.set(new VexUIMap.Layer(layer.x(), layer.y(), layer.width(), layer.height(), disabled));
            } else if (layer.element() instanceof VexUITexture texture
                && layer.y() >= 54 && layer.y() + texture.height() <= 144
                && layer.x() < right && layer.x() + texture.width() > POPUP_X) {
                int end = layer.x() + texture.width();
                if (layer.x() < POPUP_X) {
                    iterator.set(bridgeFragment(layer.x(), layer.y(), POPUP_X - layer.x(), 0xE036));
                } else if (end > right) {
                    iterator.set(bridgeFragment(right, layer.y(), end - right, 0xE037));
                } else {
                    iterator.remove();
                }
            }
        }
    }

    private VexUIMap.Layer bridgeFragment(final int x, final int y, final int width, final int glyph) {
        var texture = new VexUITexture(shared, glyph, width, 21, width + 1, Component.empty(), VexUIEntrance.SETTLED);
        return new VexUIMap.Layer(x, y, width + 1, 21, texture);
    }

    private static Key backgroundFont(final Key asset) {
        return Key.key(asset.namespace(), asset.value() + "/background");
    }

    private static Component panoramaSprites(final Key asset, final TextColor tint) {
        var sprites = Component.text();
        for (int row = 0; row < 3; row++) {
            // The low nibbles carry the same animation clock as the bitmap sprites.
            // The upper nibbles identify this as a GUI atlas draw and select its vertical position.
            var rowTint = TextColor.color(0xC0 | (tint.red() & 15), (0xB0 + row * 16) | (tint.green() & 15),
                0xF0 | (tint.blue() & 15));
            var sprite = Key.key(asset.namespace(), asset.value() + "/panorama_" + row);
            sprites.append(Component.object(ObjectContents.sprite(GUI_ATLAS, sprite)).fallback(Component.empty())
                .color(rowTint).shadowColor(ShadowColor.none()));
        }
        return sprites.build();
    }

    private static Component panorama(final Key font, final TextColor tint) {
        // The tiles and their negative advances must become available together on a backend switch.
        var trimGlyphGap = Component.text("\uE10C").font(font);
        var rewindRow = Component.text("\uE10D").font(font);
        var rows = Component.text();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                rows.append(Component.text(Character.toString(0xE100 + row * 4 + column))
                    .font(font).color(tint).shadowColor(ShadowColor.none()));
                rows.append(trimGlyphGap);
            }
            rows.append(rewindRow);
        }
        return rows.build();
    }

    void hotspot(final int x, final int y, final int width, final int height,
                 final String action, final Component tooltip) {
        add(x, y, width, height, new VexUIHotspot(VexUISize.fixed(width, height), action, tooltip));
    }

    private void sprite(final Key font, final int glyph, final int x, final int y, final int width,
                        final int height, final TextColor tint) {
        add(x, y, width + 1, height,
            new VexUITexture(font, glyph, width, height, width + 1, Component.empty(), tint));
    }

    private TextColor ink(final int delay) {
        return entrance == Entrance.SCENE ? VexUIEntrance.color(started, delay) : VexUIEntrance.SETTLED;
    }

    private TextColor pathInk(final int delay) {
        return entrance == Entrance.SETTLED ? VexUIEntrance.SETTLED : VexUIEntrance.color(started, delay);
    }

    private static int islandX(final int slot) {
        return 27 + slot * 74;
    }

    private static int islandY(final int slot) {
        return slot % 2 == 0 ? 72 : 63;
    }
}
