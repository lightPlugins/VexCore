package dev.vexsoft.core.paper.vexui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.kyori.adventure.text.Component;

/** Bounded quantity controls with one-step, large-step and maximum actions. */
public record VexUIQuantitySelector(int value, int minimum, int maximum, int largeStep) {

    /** Validates the current quantity and an unambiguous large increment. */
    public VexUIQuantitySelector {
        if (minimum < 0 || maximum < minimum || value < minimum || value > maximum || largeStep < 2) {
            throw new IllegalArgumentException("Invalid quantity selector bounds");
        }
    }

    /** Builds controls using label keys minusN, minus1, plus1, plusN and max; clamping is automatic. */
    public VexUIRow controls(
        final String prefix,
        final Function<String, Component> labels,
        final IntFunction<Component> valueLabel,
        final IntConsumer changed,
        final VexUIStyleSheet style,
        final Map<String, VexUIAction> actions
    ) {
        List<VexUIElement> children = new ArrayList<>();
        children.add(button(prefix, "minus" + largeStep, Math.max(minimum, (long) value - largeStep),
            labels, changed, style, actions));
        children.add(button(prefix, "minus1", Math.max(minimum, value - 1L), labels, changed, style, actions));
        children.add(new VexUIText(new VexUISize(25, style.buttonHeight(), 0), valueLabel.apply(value), true));
        children.add(button(prefix, "plus1", Math.min(maximum, value + 1L), labels, changed, style, actions));
        children.add(button(prefix, "plus" + largeStep, Math.min(maximum, (long) value + largeStep),
            labels, changed, style, actions));
        children.add(button(prefix, "max", maximum, labels, changed, style, actions));
        return new VexUIRow(VexUISize.content(), style.gap(), children);
    }

    private VexUIButton button(
        final String prefix,
        final String suffix,
        final long next,
        final Function<String, Component> labels,
        final IntConsumer changed,
        final VexUIStyleSheet style,
        final Map<String, VexUIAction> actions
    ) {
        String action = prefix + "." + suffix;
        VexUIActions.register(actions, action, ignored -> changed.accept((int) next));
        return new VexUIButton(new VexUISize(-1, style.buttonHeight(), 1), new VexUIText(labels.apply(suffix)),
            style.normal(), value != next, action, Component.empty()).withRole(VexUIButtonRole.NORMAL);
    }
}
