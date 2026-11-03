package dev.vexsoft.core.paper.vexui.runtime;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.VexUISearch;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;

/** Captures dialog transport while production sessions, layout and registry creation remain active. */
@Dependencies
public final class VexUITestDialogService implements VexUIDialogService {

    final List<VexUIRenderedFrame> shown = new ArrayList<>();
    final List<VexUIFrame> frames = new ArrayList<>();
    Key closeAction;
    Key searchSubmit;
    Key searchCancel;
    int closed;

    public VexUITestDialogService(final VexServiceRegistry services) {
    }

    @Override
    public void show(final Player player, final VexUIFrame frame, final VexUIRenderedFrame rendered,
                     final Key closeAction) {
        shown.add(rendered);
        frames.add(frame);
        this.closeAction = closeAction;
    }

    @Override
    public void search(final Player player, final VexUISearch form, final Key submit, final Key cancel) {
        searchSubmit = submit;
        searchCancel = cancel;
    }

    @Override
    public void close(final Player player) {
        closed++;
    }
}
