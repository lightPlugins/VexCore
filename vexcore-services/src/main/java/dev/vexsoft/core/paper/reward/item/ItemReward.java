package dev.vexsoft.core.paper.reward.item;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.execution.ExecutionDescription;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.paper.service.reward.RewardItemService;
import dev.vexsoft.core.reward.CompiledReward;
import dev.vexsoft.core.reward.Reward;
import dev.vexsoft.core.reward.RewardBehavior;
import dev.vexsoft.core.reward.RewardResult;
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
            int minimum = range.isEmpty() ? integer(amount == null ? 1 : amount) : integer(range.get("min"));
            int maximum = range.isEmpty() ? minimum : integer(range.get("max"));
            if (minimum < 1 || maximum < minimum || maximum > 1_000_000) {
                throw new IllegalArgumentException("Item amounts must be between 1 and 1000000");
            }
            items.validate(reference);
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

    private record Definition(RewardItem item, int minimum, int maximum) {
    }

    private record Configured(List<Definition> definitions, RewardItemService items) implements CompiledReward {

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
            List<Component> descriptions = new ArrayList<>();
            Player viewer = context.player().requirePlatformPlayer(Player.class);
            for (Definition definition : definitions) {
                int amount = ThreadLocalRandom.current().nextInt(definition.minimum(), definition.maximum() + 1);
                ItemStack prototype = items.create(definition.item());
                descriptions.add(Component.text(amount + "\u00D7 ").append(items.name(viewer, definition.item())));
                int remaining = amount;
                while (remaining > 0) {
                    ItemStack stack = prototype.clone();
                    int count = Math.min(remaining, prototype.getMaxStackSize());
                    stack.setAmount(count);
                    stacks.add(stack);
                    remaining -= count;
                }
            }
            return new Prepared(List.copyOf(stacks), List.copyOf(descriptions), items);
        }

        @Override
        public RewardResult grant(final PlayerExecutionContext context) {
            return prepare(context).grant(context);
        }

        @Override
        public Component describe(final PlayerExecutionContext context) {
            Player viewer = context.player().requirePlatformPlayer(Player.class);
            return Component.join(JoinConfiguration.commas(true), definitions.stream()
                .map(definition -> Component.text(definition.minimum() == definition.maximum()
                    ? definition.minimum() + "\u00D7 "
                    : definition.minimum() + "\u2013" + definition.maximum() + "\u00D7 ")
                    .append(items.name(viewer, definition.item()))).toList());
        }
    }

    private record Prepared(List<ItemStack> stacks, List<Component> descriptions,
                            RewardItemService items) implements CompiledReward {

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
            items.deliver(context.player(), stacks);
            return RewardResult.success();
        }

        @Override
        public Component describe(final PlayerExecutionContext context) {
            return Component.join(JoinConfiguration.commas(true), descriptions);
        }

        @Override
        public List<ExecutionDescription> describeEntries(final PlayerExecutionContext context) {
            return descriptions.stream().map(description -> new ExecutionDescription("", Map.of(), description))
                .toList();
        }
    }
}
