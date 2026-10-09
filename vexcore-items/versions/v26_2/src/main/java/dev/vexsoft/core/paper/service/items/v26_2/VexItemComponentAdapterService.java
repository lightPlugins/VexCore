package dev.vexsoft.core.paper.service.items.v26_2;

import com.destroystokyo.paper.profile.ProfileProperty;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.items.VexArmorTrim;
import dev.vexsoft.core.paper.items.VexAdventureBlocks;
import dev.vexsoft.core.paper.items.VexMiningTool;
import dev.vexsoft.core.paper.items.VexComponentKey;
import dev.vexsoft.core.paper.items.VexComponentTarget;
import dev.vexsoft.core.paper.items.VexCustomModelData;
import dev.vexsoft.core.paper.items.VexEnchantments;
import dev.vexsoft.core.paper.items.VexItemAttributes;
import dev.vexsoft.core.paper.items.VexPlayerHeadProfile;
import dev.vexsoft.core.paper.items.VexTooltipDisplay;
import dev.vexsoft.core.paper.items.VexUseCooldown;
import dev.vexsoft.core.paper.items.internal.VexComponentOperation;
import dev.vexsoft.core.paper.items.internal.VexComponentOperationType;
import dev.vexsoft.core.paper.items.internal.VexComponentPatch;
import dev.vexsoft.core.paper.items.service.ItemComponentAdapterService;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.ItemArmorTrim;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.datacomponent.item.Tool;
import io.papermc.paper.datacomponent.item.UseCooldown;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.HashSet;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import io.papermc.paper.block.BlockPredicate;
import io.papermc.paper.datacomponent.item.ItemAdventurePredicate;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.util.TriState;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.ArmorTrim;

/** Applies VexCore item component patches through the Minecraft 26.2 item API. */
@Dependencies
@SuppressWarnings("UnstableApiUsage")
public class VexItemComponentAdapterService implements ItemComponentAdapterService {

    private final Map<VexAdventureBlocks, ItemAdventurePredicate> adventurePredicates = new ConcurrentHashMap<>();
    private final Map<VexMiningTool, Tool> miningTools = new ConcurrentHashMap<>();

    public VexItemComponentAdapterService(final VexServiceRegistry services) {
        Objects.requireNonNull(services, "services");
    }

    @Override
    public void apply(final ItemStack itemStack, final VexComponentPatch patch) {
        ItemStack checkedItem = Objects.requireNonNull(itemStack, "itemStack");

        Objects.requireNonNull(patch, "patch").getOperations().forEach((component, operation) -> {
            if (component.getTarget() == VexComponentTarget.ITEM) {
                apply(checkedItem, component.getKey(), operation);
            }
        });
    }

    @Override
    public void clearPresentation(final ItemStack itemStack) {
        ItemStack checkedItem = Objects.requireNonNull(itemStack, "itemStack");

        checkedItem.resetData(DataComponentTypes.CUSTOM_NAME);
        checkedItem.resetData(DataComponentTypes.LORE);
    }

    protected void apply(final ItemStack itemStack, final VexComponentKey key, final VexComponentOperation operation) {
        switch (key) {
            case MAX_STACK_SIZE -> applyValue(itemStack, DataComponentTypes.MAX_STACK_SIZE, operation, Integer.class);
            case USE_COOLDOWN -> applyUseCooldown(itemStack, operation);
            case CAN_BREAK -> applyAdventureBreaks(itemStack, operation);
            case DAMAGE -> applyValue(itemStack, DataComponentTypes.DAMAGE, operation, Integer.class);
            case MAX_DAMAGE -> applyValue(itemStack, DataComponentTypes.MAX_DAMAGE, operation, Integer.class);
            case ENCHANTMENT_GLINT ->
                applyValue(itemStack, DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, operation, Boolean.class);
            case ITEM_MODEL -> applyValue(itemStack, DataComponentTypes.ITEM_MODEL, operation, Key.class);
            case TOOLTIP_STYLE -> applyValue(itemStack, DataComponentTypes.TOOLTIP_STYLE, operation, Key.class);
            case RARITY -> applyValue(itemStack, DataComponentTypes.RARITY, operation, ItemRarity.class);
            case DYED_COLOR, TRIM, ATTRIBUTE_MODIFIERS, TOOLTIP_DISPLAY -> applyAppearance(itemStack, key, operation);
            case ENCHANTMENTS -> applyEnchantments(itemStack, operation);
            case CUSTOM_MODEL_DATA -> applyCustomModelData(itemStack, operation);
            case PLAYER_HEAD_PROFILE -> applyPlayerHeadProfile(itemStack, operation);
            case UNBREAKABLE -> applyFlag(itemStack, DataComponentTypes.UNBREAKABLE, operation);
            case DISPLAY_NAME, LORE ->
                throw new IllegalArgumentException(key + " must be handled by the packet presentation layer");
        }
    }

    private <T> void applyValue(
        final ItemStack itemStack,
        final DataComponentType.Valued<T> component,
        final VexComponentOperation operation,
        final Class<T> valueType
    ) {
        switch (operation.getType()) {
            case SET -> itemStack.setData(component, valueType.cast(operation.getValue()));
            case UNSET -> itemStack.unsetData(component);
            case RESET -> itemStack.resetData(component);
        }
    }

    private void applyFlag(
        final ItemStack itemStack,
        final DataComponentType.NonValued component,
        final VexComponentOperation operation
    ) {
        switch (operation.getType()) {
            case SET -> itemStack.setData(component);
            case UNSET -> itemStack.unsetData(component);
            case RESET -> itemStack.resetData(component);
        }
    }

    private void applyEnchantments(final ItemStack itemStack, final VexComponentOperation operation) {
        if (operation.getType() != VexComponentOperationType.SET) {
            applyWithoutValue(itemStack, DataComponentTypes.ENCHANTMENTS, operation);

            return;
        }

        VexEnchantments enchantments = (VexEnchantments) operation.getValue();
        ItemEnchantments.Builder builder = ItemEnchantments.itemEnchantments();

        for (Map.Entry<Enchantment, Integer> entry : enchantments.getEnchantments().entrySet()) {
            builder.add(entry.getKey(), entry.getValue());
        }

        itemStack.setData(DataComponentTypes.ENCHANTMENTS, builder.build());
    }

    private void applyUseCooldown(final ItemStack itemStack, final VexComponentOperation operation) {
        if (operation.getType() != VexComponentOperationType.SET) {
            applyWithoutValue(itemStack, DataComponentTypes.USE_COOLDOWN, operation);
            return;
        }

        VexUseCooldown value = (VexUseCooldown) operation.getValue();
        var builder = UseCooldown.useCooldown(value.ticks() / 20.0F);
        if (value.group() != null) {
            builder.cooldownGroup(value.group());
        }
        itemStack.setData(DataComponentTypes.USE_COOLDOWN, builder.build());
    }

    private void applyAdventureBreaks(final ItemStack stack, final VexComponentOperation operation) {
        if (operation.getType() != VexComponentOperationType.SET) {
            applyWithoutValue(stack, DataComponentTypes.CAN_BREAK, operation);
            return;
        }
        synchronizeAdventureBreaks(stack, (VexAdventureBlocks) operation.getValue());
    }

    @Override
    public boolean synchronizeAdventureBreaks(final ItemStack stack, final VexAdventureBlocks value) {
        if (adventurePredicates.size() >= 256 && !adventurePredicates.containsKey(value)) {
            adventurePredicates.clear();
        }
        var expected = value.blocks().isEmpty() ? null : adventurePredicates.computeIfAbsent(value, allowed -> {
            var keys = allowed.blocks().stream().sorted(Comparator.comparing(NamespacedKey::asString))
                .map(key -> TypedKey.create(RegistryKey.BLOCK, Key.key(key.asString()))).toList();
            return ItemAdventurePredicate.itemAdventurePredicate(List.of(
                BlockPredicate.predicate().blocks(RegistrySet.keySet(RegistryKey.BLOCK, keys)).build()));
        });
        boolean changed = !Objects.equals(stack.getData(DataComponentTypes.CAN_BREAK), expected);
        if (changed) {
            if (expected == null) {
                stack.unsetData(DataComponentTypes.CAN_BREAK);
            } else {
                stack.setData(DataComponentTypes.CAN_BREAK, expected);
            }
        }
        var display = stack.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        if (expected != null && (display == null || !display.hiddenComponents().contains(DataComponentTypes.CAN_BREAK))) {
            var hidden = new HashSet<DataComponentType>(display == null ? Set.of() : display.hiddenComponents());
            hidden.add(DataComponentTypes.CAN_BREAK);
            stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .hideTooltip(display != null && display.hideTooltip())
                .addHiddenComponents(hidden.toArray(DataComponentType[]::new)).build());
            changed = true;
        }
        return changed;
    }

    @Override
    public boolean synchronizeMiningTool(final ItemStack stack, final VexMiningTool value) {
        if (miningTools.size() >= 256 && !miningTools.containsKey(value)) {
            miningTools.clear();
        }
        Tool expected = miningTools.computeIfAbsent(value, plan -> {
            var builder = Tool.tool().defaultMiningSpeed(1).damagePerBlock(0);
            var entries = plan.ticks().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().asString())).toList();
            for (var entry : entries) {
                var key = TypedKey.create(RegistryKey.BLOCK, Key.key(entry.getKey().asString()));
                float hardness = Registry.BLOCK.getOrThrow(entry.getKey()).getHardness();
                if (!Float.isFinite(hardness) || hardness < 0) {
                    throw new IllegalArgumentException("Mining rule requires a breakable block: " + entry.getKey());
                }
                // Correct-tool progress is speed / hardness / 30. Vanilla handles air/water penalties on both sides.
                float speed = hardness == 0 ? 1 : (float) (hardness * 30.0D / entry.getValue());
                builder.addRule(Tool.rule(RegistrySet.keySet(RegistryKey.BLOCK, List.of(key)), speed, TriState.TRUE));
            }
            return builder.build();
        });
        if (Objects.equals(stack.getData(DataComponentTypes.TOOL), expected)) {
            return false;
        }
        stack.setData(DataComponentTypes.TOOL, expected);
        return true;
    }

    private void applyCustomModelData(final ItemStack itemStack, final VexComponentOperation operation) {
        if (operation.getType() != VexComponentOperationType.SET) {
            applyWithoutValue(itemStack, DataComponentTypes.CUSTOM_MODEL_DATA, operation);

            return;
        }

        VexCustomModelData value = (VexCustomModelData) operation.getValue();
        CustomModelData.Builder builder = CustomModelData.customModelData()
            .addFloats(value.getFloatValues())
            .addFlags(value.getFlagValues())
            .addStrings(value.getStringValues())
            .addColors(value.getColorValues());

        itemStack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, builder.build());
    }

    private void applyPlayerHeadProfile(final ItemStack itemStack, final VexComponentOperation operation) {
        if (operation.getType() != VexComponentOperationType.SET) {
            applyWithoutValue(itemStack, DataComponentTypes.PROFILE, operation);

            return;
        }

        VexPlayerHeadProfile value = (VexPlayerHeadProfile) operation.getValue();
        ProfileProperty property = value.signature() == null ? new ProfileProperty("textures", value.texture())
            : new ProfileProperty("textures", value.texture(), value.signature());
        ResolvableProfile profile = ResolvableProfile.resolvableProfile().addProperty(property).build();

        itemStack.setData(DataComponentTypes.PROFILE, profile);
    }

    private void applyWithoutValue(
        final ItemStack itemStack,
        final DataComponentType component,
        final VexComponentOperation operation
    ) {
        switch (operation.getType()) {
            case SET -> throw new IllegalArgumentException("A value is required for this component");
            case UNSET -> itemStack.unsetData(component);
            case RESET -> itemStack.resetData(component);
        }
    }

    @Override
    public VexArmorTrim armorTrim(NamespacedKey pattern, NamespacedKey material) {
        var resolvedPattern = Registry.TRIM_PATTERN.get(Objects.requireNonNull(pattern, "pattern"));
        var resolvedMaterial = Registry.TRIM_MATERIAL.get(Objects.requireNonNull(material, "material"));

        if (resolvedPattern == null) {
            throw new IllegalArgumentException("Unknown armor trim pattern: " + pattern);
        }

        if (resolvedMaterial == null) {
            throw new IllegalArgumentException("Unknown armor trim material: " + material);
        }

        return new VexArmorTrim(resolvedPattern, resolvedMaterial);
    }

    private static DataComponentType appearanceComponent(VexComponentKey key) {
        return switch (key) {
            case DYED_COLOR -> DataComponentTypes.DYED_COLOR;
            case TRIM -> DataComponentTypes.TRIM;
            case ATTRIBUTE_MODIFIERS -> DataComponentTypes.ATTRIBUTE_MODIFIERS;
            case TOOLTIP_DISPLAY -> DataComponentTypes.TOOLTIP_DISPLAY;
            case UNBREAKABLE -> DataComponentTypes.UNBREAKABLE;
            case ENCHANTMENTS -> DataComponentTypes.ENCHANTMENTS;
            case DAMAGE -> DataComponentTypes.DAMAGE;
            case CAN_BREAK -> DataComponentTypes.CAN_BREAK;
            default -> throw new IllegalArgumentException("Unsupported appearance component: " + key);
        };
    }

    private void applyAppearance(ItemStack stack, VexComponentKey key, VexComponentOperation operation) {
        if (operation.getType() != VexComponentOperationType.SET) {
            applyWithoutValue(stack, appearanceComponent(key), operation);

            return;
        }

        switch (key) {
            case DYED_COLOR -> stack.setData(
                DataComponentTypes.DYED_COLOR,
                DyedItemColor.dyedItemColor(Color.fromRGB((Integer) operation.getValue()))
            );
            case TRIM -> {
                VexArmorTrim trim = (VexArmorTrim) operation.getValue();

                stack.setData(
                    DataComponentTypes.TRIM,
                    ItemArmorTrim.itemArmorTrim(new ArmorTrim(trim.material(), trim.pattern())).build()
                );
            }
            case ATTRIBUTE_MODIFIERS -> {
                var builder = ItemAttributeModifiers.itemAttributes();

                ((VexItemAttributes) operation.getValue()).entries()
                    .forEach(entry -> builder.addModifier(entry.attribute(), entry.modifier()));
                stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build());
            }
            case TOOLTIP_DISPLAY -> {
                VexTooltipDisplay display = (VexTooltipDisplay) operation.getValue();
                var builder = TooltipDisplay.tooltipDisplay().hideTooltip(display.hideTooltip());

                display.hiddenComponents().forEach(hidden -> builder.addHiddenComponents(appearanceComponent(hidden)));
                stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, builder.build());
            }
            default -> throw new IllegalArgumentException("Unsupported appearance component: " + key);
        }
    }
}
