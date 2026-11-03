package dev.vexsoft.core.paper.vexui.runtime;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.VexUISearch;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;

/** Uses Paper 26.2 dialogs with NONE after-action to preserve uninterrupted mouse interaction. */
@Dependencies
@SuppressWarnings("UnstableApiUsage")
public final class VexUIPaperDialogService implements VexUIDialogService {

    public VexUIPaperDialogService(final VexServiceRegistry services) {
        Objects.requireNonNull(services, "services");
    }

    @Override
    public void show(final Player player, final VexUIFrame frame, final VexUIRenderedFrame rendered,
                     final Key closeAction) {
        player.showDialog(Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(frame.title()).pause(false).canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(rendered.body(), rendered.width() + 10))).build())
            // A native action is required for the client's Escape path to send cancellation to the session.
            // Empty multi-action lists also fail the Minecraft client codec even though the Paper builder accepts them.
            .type(DialogType.notice(ActionButton.create(frame.closeLabel(), null,
                frame.compactCloseButton() ? 80 : frame.width(), DialogAction.customClick(closeAction, null))))));
    }

    @Override
    public void search(final Player player, final VexUISearch form, final Key submit, final Key cancel) {
        player.showDialog(Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(form.title()).pause(false).canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .inputs(List.of(DialogInput.text("query", 280, form.label(), true,
                    form.initial().substring(0, Math.min(128, form.initial().length())), 128, null))).build())
            .type(DialogType.confirmation(
                ActionButton.create(form.submit(), null, 135, DialogAction.customClick(submit, null)),
                ActionButton.create(form.cancel(), null, 135, DialogAction.customClick(cancel, null))))));
    }

    @Override
    public void close(final Player player) {
        player.closeDialog();
    }
}
