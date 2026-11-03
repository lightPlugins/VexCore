package dev.vexsoft.core.paper.vexui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;

/** Reusable child confirmation view; the parent instance is retained and restored after either choice. */
public final class VexUIConfirmation implements VexUIView {

    private final Component title;
    private final Component close;
    private final VexUIElement content;
    private final Component confirm;
    private final Component cancel;
    private final int width;
    private final boolean dangerous;
    private final Runnable confirmed;
    private final AtomicBoolean accepted = new AtomicBoolean();

    /** Creates a localized confirmation; confirmed performs a domain action, not navigation, on the entity thread. */
    public VexUIConfirmation(
        final Component title,
        final Component close,
        final VexUIElement content,
        final Component confirm,
        final Component cancel,
        final int width,
        final boolean dangerous,
        final Runnable confirmed
    ) {
        this.title = Objects.requireNonNull(title, "title");
        this.close = Objects.requireNonNull(close, "close");
        this.content = Objects.requireNonNull(content, "content");
        this.confirm = Objects.requireNonNull(confirm, "confirm");
        this.cancel = Objects.requireNonNull(cancel, "cancel");
        this.confirmed = Objects.requireNonNull(confirmed, "confirmed");
        if (width < 100 || width > 960) {
            throw new IllegalArgumentException("Invalid confirmation width");
        }
        this.width = width;
        this.dangerous = dangerous;
    }

    @Override
    public VexUIFrame render(final VexUISession session) {
        Map<String, VexUIAction> actions = new LinkedHashMap<>();
        VexUIActions.register(actions, "confirmation.accept", viewer -> {
            if (accepted.compareAndSet(false, true)) {
                try {
                    confirmed.run();
                } finally {
                    viewer.back();
                }
            }
        });
        var style = session.styleSheet();
        var accept = new VexUIButton(new VexUISize(-1, style.buttonHeight(), 1), new VexUIText(confirm),
            style.primary(), !accepted.get(), "confirmation.accept", Component.empty())
            .withRole(dangerous ? VexUIButtonRole.DANGER : VexUIButtonRole.PRIMARY);
        var controls = new VexUIRow(style.gap(), VexUINavigation.back(session, cancel, actions), accept);
        return new VexUIFrame(title, close, width, new VexUIColumn(style.gap(), content, controls), actions);
    }
}
