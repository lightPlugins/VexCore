package dev.vexsoft.core.paper.service.screenui;

import dev.vexsoft.core.paper.screenui.TextBlockLayout;
import dev.vexsoft.core.paper.screenui.UiNode;
import dev.vexsoft.core.paper.screenui.UiPanelBounds;
import dev.vexsoft.core.paper.screenui.UiPanelLayout;
import dev.vexsoft.core.paper.screenui.version.ScreenUiVersionDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

/** Pure layout compiler; one complete panel is committed as one coalesced screen element. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ScreenUiPanelRenderer {

    /** Measures a content tree without allocating any player or rendering state. */
    public static UiPanelBounds measure(UiNode root, UiPanelLayout layout) {
        return prepare(root, layout, null).bounds();
    }

    public static Prepared prepare(UiNode root, UiPanelLayout layout, ScreenUiVersionDefinition version) {
        Measured measured = measure(root, layout.maxWidth() - 2 * layout.style().padding(), layout, 0, new int[]{256});
        if (measured.width() == 0 && measured.height() == 0) {
            return new Prepared(Component.empty(), new UiPanelBounds(layout.x(), layout.y(), 0, 0, Map.of()));
        }
        int width = measured.width() + 2 * layout.style().padding();
        int height = measured.height() + 2 * layout.style().padding();
        int x = layout.x() - layout.anchor().ordinal() % 3 * width / 2;
        int y = layout.y() - (layout.anchor().ordinal() / 3 == 2 ? height : 0);
        if (height > 256 || y < -256 || y + height > 256) {
            throw new IllegalArgumentException("Panel exceeds the supported vertical area; reduce content or scale");
        }
        var output = Component.text();
        if (version != null) {
            output.append(background(width, height, layout.toBuilder().x(x).y(y).build(), version));
        }
        Map<String, UiPanelBounds.Rect> slots = new HashMap<>();
        draw(
            measured, x + layout.style().padding(), y + layout.style().padding(), measured.width(),
            layout, version, slots, output
        );
        return new Prepared(output.build(), new UiPanelBounds(x, y, width, height, slots));
    }

    public static Component background(
        int width, int height, UiPanelLayout layout,
        ScreenUiVersionDefinition version
    ) {
        if (!layout.style().background() || layout.style().opacity() == 0) {
            return Component.empty();
        }
        if (width < 1 || width > 512 || height < 1 || height > 256) {
            throw new IllegalArgumentException("Invalid panel background dimensions");
        }
        int payload = ((width - 1) << 15) | ((height - 1) << 7)
            | (int) Math.round(layout.style().opacity() * 100);
        int x = layout.x() + version.horizontalTransport(layout.y());
        Component glyph = Component.text(Character.toString(0xE100 + layout.style().radius()), TextColor.color(payload))
            .font(version.panelFont(layout.anchor(), layout.y(), layout.scale(), layout.style().radius()))
            .shadowColor(ShadowColor.none());
        return Component.empty().append(ScreenUiRenderer.space(x)).append(glyph)
            .append(ScreenUiRenderer.space(-x - 17));
    }

    private static Measured measure(UiNode node, int available, UiPanelLayout layout, int depth, int[] budget) {
        if (--budget[0] < 0 || depth > 12 || available < 8) {
            throw new IllegalArgumentException("Panel content cannot fit its configured width");
        }
        if (node instanceof UiNode.Text text) {
            var lines = ScreenUiRenderer.layout(
                text.lines(), TextBlockLayout.builder().maxWidth(available)
                    .lineSpacing(layout.style().gap()).anchor(layout.anchor()).build()
            );
            int width = lines.stream().mapToInt(ScreenUiRenderer.Line::width).max().orElse(0);
            int height = width == 0 ? 0 : lines.size() * (12 + layout.style().gap()) - layout.style().gap();
            return new Measured(node, width, height, List.of());
        }
        if (node instanceof UiNode.Space space) {
            if (space.width() > available) {
                throw new IllegalArgumentException("Reserved panel space exceeds its width");
            }
            return new Measured(node, space.width(), space.height(), List.of());
        }
        if (node instanceof UiNode.Avatar) {
            if (available < 24) {
                throw new IllegalArgumentException("Avatar needs 24 pixels");
            }
            return new Measured(node, 24, 24, List.of());
        }
        UiNode.Group group = (UiNode.Group) node;
        List<Measured> children = new ArrayList<>();
        for (UiNode child : group.children()) {
            Measured result = measure(child, available, layout, depth + 1, budget);
            if (result.width() > 0 || result.height() > 0) {
                children.add(result);
            }
        }
        int gaps = Math.max(0, children.size() - 1) * layout.style().gap();
        if (group.horizontal()) {
            int total = children.stream().mapToInt(Measured::width).sum() + gaps;
            if (total > available) {
                int largest = 0;
                for (int index = 1; index < children.size(); index++) {
                    if (children.get(index).width() > children.get(largest).width()) {
                        largest = index;
                    }
                }
                Measured child = children.get(largest);
                children.set(
                    largest,
                    measure(child.node(), child.width() - total + available, layout, depth + 1, budget)
                );
            }
        }
        int width = group.horizontal() ? children.stream().mapToInt(Measured::width).sum() + gaps
            : children.stream().mapToInt(Measured::width).max().orElse(0);
        int height = group.horizontal() ? children.stream().mapToInt(Measured::height).max().orElse(0)
            : children.stream().mapToInt(Measured::height).sum() + gaps;
        return new Measured(node, width, height, List.copyOf(children));
    }

    private static void draw(
        Measured measured, int x, int y, int availableWidth, UiPanelLayout layout,
        ScreenUiVersionDefinition version, Map<String, UiPanelBounds.Rect> slots,
        TextComponent.Builder output
    ) {
        if (slots.put(measured.node().id(), new UiPanelBounds.Rect(x, y, measured.width(), measured.height()))
            != null) {
            throw new IllegalArgumentException("Duplicate panel node id: " + measured.node().id());
        }
        if (measured.node() instanceof UiNode.Text text && version != null) {
            int textX = x + switch (layout.textAlign()) {
                case LEFT -> 0;
                case CENTER -> availableWidth / 2;
                case RIGHT -> availableWidth;
            };
            output.append(ScreenUiRenderer.text(
                text.lines(), TextBlockLayout.builder().anchor(layout.anchor())
                    .x(textX).y(y).maxWidth(Math.max(8, availableWidth)).lineSpacing(layout.style().gap())
                    .horizontalAlignment(layout.textAlign()).scale(layout.scale()).build(), version
            ));
        } else if (measured.node() instanceof UiNode.Avatar avatar && version != null) {
            for (int row = 0; row < 8; row++) {
                int offset = x + version.horizontalTransport(y + row * 3);
                output.append(ScreenUiRenderer.space(offset));
                for (int column = 0; column < 8; column++) {
                    output.append(Component.text("\uE100", TextColor.color(avatar.pixels().get(row * 8 + column)))
                        .font(version.pixelFont(layout.anchor(), y + row * 3, layout.scale()))
                        .shadowColor(ShadowColor.none()));
                    output.append(ScreenUiRenderer.space(-1));
                }
                output.append(ScreenUiRenderer.space(-offset - 24));
            }
        } else if (measured.node() instanceof UiNode.Group group) {
            for (Measured child : measured.children()) {
                // Rows keep measured cells; columns share their width with text and nested columns.
                int childWidth = group.horizontal() ? child.width() : availableWidth;
                draw(child, x, y, childWidth, layout, version, slots, output);
                if (group.horizontal()) {
                    x += child.width() + layout.style().gap();
                } else {
                    y += child.height() + layout.style().gap();
                }
            }
        }
    }

    public record Prepared(Component component, UiPanelBounds bounds) {

    }

    private record Measured(UiNode node, int width, int height, List<Measured> children) {

    }
}
