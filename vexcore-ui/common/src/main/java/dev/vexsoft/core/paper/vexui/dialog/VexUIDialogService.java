package dev.vexsoft.core.paper.vexui.dialog;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.component.VexUISearch;
import dev.vexsoft.core.paper.vexui.render.VexUIRenderedFrame;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;

/** Isolates Paper's dialog transport from deterministic layout and session behavior. */
public interface VexUIDialogService extends VexService {

    /** Replaces the current dialog without first closing it. Called on the viewer's entity scheduler. */
    void show(Player player, VexUIFrame frame, VexUIRenderedFrame rendered, Key closeAction);

    /** Displays a native search form whose result is submitted through opaque action identifiers. */
    void search(Player player, VexUISearch form, Key submit, Key cancel);

    /** Closes the currently displayed dialog on the viewer's entity scheduler. */
    void close(Player player);
}
