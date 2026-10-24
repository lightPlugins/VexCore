package dev.vexsoft.core.paper.reward.item;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.execution.ExecutionDescription;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.number.WholeAmountFormatter;
import dev.vexsoft.core.paper.service.reward.RewardItemService;
import dev.vexsoft.core.reward.CompiledReward;
import dev.vexsoft.core.reward.Reward;
import dev.vexsoft.core.reward.RewardBehavior;
import dev.vexsoft.core.reward.RewardResult;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Global items reward supporting namespaced providers, upgrades and once-selected amount ranges. */
@Dependencies(RewardItemService.class)
public final class ItemReward implements Reward {

    private final RewardItemService items;

    /** Captures the shared physical item resolver. */
    public ItemReward(final VexServiceRegistry services) {
        items = services.require(RewardItemService.class);
    }

    @Override
    public CompiledReward compile(final Object value) {
        List<?> configured = value instanceof List<?> list ? list : List.of(value);
        List<Definition> definitions = new ArrayList<>();
        for (Object entry : configured) {
            Map<?, ?> map = entry instanceof ConfigurationSection section ? section.getValues(false)
                : entry instanceof Map<?, ?> values ? values : Map.of();
            Object level = map.get("upgrade-level");
            RewardItem reference = new RewardItem(Objects.toString(map.get("key"), ""),
                level == null ? 0 : integer(level));
            Object amount = map.get("amount");
            Map<?, ?> range = amount instanceof ConfigurationSection section ? section.getValues(false)
                : amount instanceof Map<?, ?> values ? values : Map.of();
            WholeAmount minimum = quantity(range.isEmpty() ? amount == null ? 1 : amount : range.get("min"));
            WholeAmount maximum = range.isEmpty() ? minimum : quantity(range.get("max"));
            if (!minimum.isPositive() || maximum.compareTo(minimum) < 0) {
                throw new IllegalArgumentException("Item amounts require a positive, ordered range");
            }
            items.validate(reference);
            if (!items.isVirtual(reference) && maximum.compareTo(WholeAmount.of(1_000_000)) > 0) {
                throw new IllegalArgumentException("Physical item amounts must not exceed 1000000");
            }
            definitions.add(new Definition(reference, minimum, maximum));
        }
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("items reward must not be empty");
        }
        return new Configured(List.copyOf(definitions), items);
    }

    private static int integer(final Object value) {
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException("Item amount and upgrade level must be whole numbers");
        }
        return number.intValue();
    }

    private static WholeAmount quantity(final Object value) {
        if (!(value instanceof Number) && !(value instanceof String)) {
            throw new IllegalArgumentException("Item amount must be a whole amount");
        }
        return WholeAmountFormatter.parse(value.toString());
    }

    private static WholeAmount select(final Definition definition) {
        BigInteger width = definition.maximum().subtract(definition.minimum()).toBigInteger().add(BigInteger.ONE);
        if (width.equals(BigInteger.ONE)) {
            return definition.minimum();
        }
        BigInteger selected;
        do {
            selected = new BigInteger(width.bitLength(), ThreadLocalRandom.current());
        } while (selected.compareTo(width) >= 0);
        return definition.minimum().add(WholeAmount.of(selected));
    }

    private static Component join(final List<Component> descriptions) {
        // Separators inherit the consumer's quantity color, rather than the surrounding menu heading.
        Component result = Component.join(JoinConfiguration.commas(true), descriptions);
        return descriptions.isEmpty() ? result : result.color(descriptions.getFirst().color());
    }

    private record Definition(RewardItem item, WholeAmount minimum, WholeAmount maximum) {
    }

    private record VirtualGrant(RewardItem item, WholeAmount amount) {
    }

    private record Configured(List<Definition> definitions, RewardItemService items) implements CompiledReward {

        @Override
        public boolean changesInventory() {
            return definitions.stream().anyMatch(definition -> !items.isVirtual(definition.item()));
        }

        @Override
        public RewardBehavior getBehavior() {
            return RewardBehavior.ACTION;
        }

        @Override
        public boolean supportsPlayerRollback() {
            return true;
        }

        @Override
        public CompiledReward prepare(final PlayerExecutionContext context) {
            List<ItemStack> stacks = new ArrayList<>();
            List<RewardItem> physical = new ArrayList<>();
            List<VirtualGrant> virtual = new ArrayList<>();
            List<Component> descriptions = new ArrayList<>();
            Player viewer = context.player().requirePlatformPlayer(Player.class);
            for (Definition definition : definitions) {
                WholeAmount amount = select(definition);
                descriptions.add(items.describe(viewer, definition.item(), WholeAmountFormatter.format(amount)));
                if (items.isVirtual(definition.item())) {
                    virtual.add(new VirtualGrant(definition.item(), amount));
                    continue;
                }
                if (definition.maximum().compareTo(WholeAmount.of(1_000_000)) > 0) {
                    throw new IllegalArgumentException("Physical item amounts must not exceed 1000000");
                }
                ItemStack prototype = items.create(definition.item());
                physical.add(definition.item());
                int remaining = amount.intValueExact();
                while (remaining > 0) {
                    ItemStack stack = prototype.clone();
                    int count = Math.min(remaining, prototype.getMaxStackSize());
                    stack.setAmount(count);
                    stacks.add(stack);
                    remaining -= count;
                }
            }
            return new Prepared(List.copyOf(stacks), List.copyOf(physical), List.copyOf(virtual),
                List.copyOf(descriptions), items);
        }

        @Override
        public RewardResult grant(final PlayerExecutionContext context) {
            return prepare(context).grant(context);
        }

        @Override
        public Component describe(final PlayerExecutionContext context) {
            Player viewer = context.player().requirePlatformPlayer(Player.class);
            return join(definitions.stream()
                .map(definition -> items.describe(viewer, definition.item(), definition.minimum().equals(definition.maximum())
                    ? WholeAmountFormatter.format(definition.minimum())
                    : WholeAmountFormatter.format(definition.minimum()) + "\u2013"
                        + WholeAmountFormatter.format(definition.maximum())))
                .toList());
        }
    }

    private record Prepared(List<ItemStack> stacks, List<RewardItem> physical, List<VirtualGrant> virtual,
                            List<Component> descriptions,
                            RewardItemService items) implements PreparedItemReward {

        @Override
        public boolean changesInventory() {
            return !stacks.isEmpty();
        }

        @Override
        public List<ItemStack> preparedItems() {
            validatePhysical();
            return stacks.stream().map(ItemStack::clone).toList();
        }

        @Override
        public RewardBehavior getBehavior() {
            return RewardBehavior.ACTION;
        }

        @Override
        public boolean supportsPlayerRollback() {
            return true;
        }

        @Override
        public RewardResult grant(final PlayerExecutionContext context) {
            validatePhysical();
            grantVirtual(context);
            if (!stacks.isEmpty()) {
                items.deliver(context.player(), stacks);
            }
            return RewardResult.success();
        }

        @Override
        public RewardResult grantVirtual(final PlayerExecutionContext context) {
            for (VirtualGrant grant : virtual) {
                items.deliverVirtual(context.player(), grant.item(), grant.amount());
            }
            return RewardResult.success();
        }

        @Override
        public Component describe(final PlayerExecutionContext context) {
            return join(descriptions);
        }

        @Override
        public List<ExecutionDescription> describeEntries(final PlayerExecutionContext context) {
            return descriptions.stream().map(description -> new ExecutionDescription("", Map.of(), description))
                .toList();
        }

        private void validatePhysical() {
            for (RewardItem item : physical) {
                if (items.isVirtual(item)) {
                    throw new IllegalStateException("Prepared physical item changed its storage mode");
                }
            }
        }
    }
}
