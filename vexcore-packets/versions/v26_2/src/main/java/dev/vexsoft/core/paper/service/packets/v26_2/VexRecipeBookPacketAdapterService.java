package dev.vexsoft.core.paper.service.packets.v26_2;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.service.RecipeBookPacketAdapterService;
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket;
import net.minecraft.world.inventory.RecipeBookType;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** Closes server and client recipe-book state for Minecraft 26.2. */
@Dependencies
public final class VexRecipeBookPacketAdapterService implements RecipeBookPacketAdapterService {

    public VexRecipeBookPacketAdapterService(final VexServiceRegistry services) {
    }

    @Override
    public void closeRecipeBook(final Player player) {
        var handle = ((CraftPlayer) player).getHandle();
        var book = handle.getRecipeBook();
        for (RecipeBookType type : RecipeBookType.values()) {
            book.setOpen(type, false);
        }
        handle.connection.send(new ClientboundRecipeBookSettingsPacket(book.getBookSettings()));
    }
}
