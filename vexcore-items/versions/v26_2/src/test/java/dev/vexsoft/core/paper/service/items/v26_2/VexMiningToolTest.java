package dev.vexsoft.core.paper.service.items.v26_2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.items.VexMiningTool;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Verifies native per-material break speeds and their client wire representation without a running server. */
final class VexMiningToolTest {
    private static RegistryAccess registries;

    @BeforeAll
    static void initializeRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Runtime item components normally come from the loaded data pack; this fixture only needs TOOL.
        Items.PAPER.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CraftRegistry.setMinecraftRegistry(registries);
    }

    @Test
    void stableToolRulesCoverAllOresAndSurviveClientSerialization() {
        var nativeStack = new net.minecraft.world.item.ItemStack(Items.PAPER);
        var stack = CraftItemStack.asCraftMirror(nativeStack);
        var registry = (VexServiceRegistry) Proxy.newProxyInstance(VexServiceRegistry.class.getClassLoader(),
            new Class<?>[]{VexServiceRegistry.class}, (instance, method, arguments) -> null);
        var adapter = new VexItemComponentAdapterService(registry);
        var plan = new VexMiningTool(Map.of(NamespacedKey.minecraft("iron_ore"), 40.0,
            NamespacedKey.minecraft("deepslate_iron_ore"), 40.0, NamespacedKey.minecraft("copper_ore"), 30.0));
        assertTrue(adapter.synchronizeMiningTool(stack, plan));
        assertFalse(adapter.synchronizeMiningTool(stack, plan));
        assertDuration(nativeStack, Blocks.IRON_ORE.defaultBlockState(), 40);
        assertDuration(nativeStack, Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(), 40);
        assertDuration(nativeStack, Blocks.COPPER_ORE.defaultBlockState(), 30);
        // Sweeping across stone or wood uses the default speed without replacing any ore rule.
        assertEquals(1.0F, nativeStack.getDestroySpeed(Blocks.STONE.defaultBlockState()));
        assertEquals(1.0F, nativeStack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()));
        assertFalse(adapter.synchronizeMiningTool(stack, plan));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            var codec = DataComponents.TOOL.streamCodec();
            codec.encode(buffer, nativeStack.get(DataComponents.TOOL));
            var clientStack = new net.minecraft.world.item.ItemStack(Items.PAPER);
            clientStack.set(DataComponents.TOOL, codec.decode(buffer));
            assertDuration(clientStack, Blocks.IRON_ORE.defaultBlockState(), 40);
            assertDuration(clientStack, Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(), 40);
            assertDuration(clientStack, Blocks.COPPER_ORE.defaultBlockState(), 30);
        } finally {
            buffer.release();
        }
    }

    private static void assertDuration(final net.minecraft.world.item.ItemStack stack, final BlockState state,
        final double ticks) {
        assertTrue(stack.isCorrectToolForDrops(state));
        double progress = stack.getDestroySpeed(state)
            / state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) / 30.0;
        assertEquals(1.0 / ticks, progress, 0.000001);
    }
}
