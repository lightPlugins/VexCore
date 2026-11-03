package dev.vexsoft.core.paper.commands;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.command.Command;
import dev.vexsoft.core.paper.command.CommandRoot;
import dev.vexsoft.core.paper.command.VexCommandSource;
import dev.vexsoft.core.paper.vexui.debug.VexUIDebugShopService;
import org.bukkit.entity.Player;

/** Opens the opt-in VexUI test shop under VexCore's debug command. */
@CommandRoot(name = "vexcore", description = "Manages VexCore")
@Dependencies(VexUIDebugShopService.class)
public final class VexUICommand {

    private final VexUIDebugShopService shop;

    public VexUICommand(final VexServiceRegistry services) {
        shop = services.require(VexUIDebugShopService.class);
    }

    /** Opens the free vanilla-item shop used to validate the VexUI framework. */
    @Command(value = "debug vexui shop", permission = "vexcore.command.debug.vexui", playerOnly = true)
    public int shop(final VexCommandSource source) {
        shop.open((Player) source.getSender());
        return 1;
    }

    /** Opens reusable components, reactive state and asynchronous feedback examples. */
    @Command(value = "debug vexui components", permission = "vexcore.command.debug.vexui", playerOnly = true)
    public int components(final VexCommandSource source) {
        shop.openComponents((Player) source.getSender(), false);
        return 1;
    }

    /** Opens the component playground with box boundaries and action tooltips. */
    @Command(value = "debug vexui inspect", permission = "vexcore.command.debug.vexui", playerOnly = true)
    public int inspect(final VexCommandSource source) {
        shop.openComponents((Player) source.getSender(), true);
        return 1;
    }
}
