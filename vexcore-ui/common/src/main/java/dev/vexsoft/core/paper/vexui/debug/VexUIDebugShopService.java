package dev.vexsoft.core.paper.vexui.debug;

import dev.vexsoft.core.api.service.registry.VexService;
import org.bukkit.entity.Player;

/** Opens the owner-local, unlimited-credit VexUI acceptance shop. */
public interface VexUIDebugShopService extends VexService {

    /** Opens a fresh test shop whose purchases grant vanilla items without touching an economy. */
    void open(Player player);

    /** Opens the reactive component playground, optionally with layout and action inspection enabled. */
    void openComponents(Player player, boolean inspect);
}
