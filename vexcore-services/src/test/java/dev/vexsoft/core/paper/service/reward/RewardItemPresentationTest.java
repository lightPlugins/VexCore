package dev.vexsoft.core.paper.service.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.reward.item.RewardItem;
import dev.vexsoft.core.paper.reward.item.RewardItemProvider;
import java.lang.reflect.Proxy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** Presentation hooks preserve client translations, cannot reroll rewards, and are removed on owner cleanup. */
final class RewardItemPresentationTest {

    @Test
    void styledDescriptionsKeepTranslatedNamesAndRestoreTheDefaultAfterClosing() throws Exception {
        var services = (VexServiceRegistry) Proxy.newProxyInstance(VexServiceRegistry.class.getClassLoader(),
            new Class<?>[]{VexServiceRegistry.class}, (object, method, arguments) -> null);
        var items = new VexRewardItemService(services);
        items.register("test", new RewardItemProvider() {
            @Override
            public void validate(final RewardItem item) {
            }

            @Override
            public ItemStack create(final RewardItem item) {
                throw new AssertionError("Formatting must not create a physical item");
            }

            @Override
            public Component name(final Player viewer, final RewardItem item) {
                return Component.translatable("item.minecraft.iron_ingot", NamedTextColor.GRAY);
            }
        });
        var reference = new RewardItem("test:iron", 0);
        var formatting = items.registerPresentation("test", (viewer, item, amount, name) ->
            Component.text(amount + "× ", NamedTextColor.DARK_GRAY).append(name));
        Component result = items.describe(null, reference, "2–4");
        assertEquals(NamedTextColor.DARK_GRAY, result.color());
        var name = (TranslatableComponent) result.children().getFirst();
        assertEquals("item.minecraft.iron_ingot", name.key());
        assertEquals(NamedTextColor.GRAY, name.color());
        assertThrows(IllegalArgumentException.class, () -> items.registerPresentation("test",
            (viewer, item, amount, title) -> title));
        formatting.close();
        assertEquals(null, items.describe(null, reference, "2").color());
    }
}
