package dev.vexsoft.core.common.reward.service;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.player.TransactionalPlayerData;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.reward.RewardService;
import dev.vexsoft.core.common.execution.service.ExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.execution.service.ExecutionComponentKind;
import dev.vexsoft.core.execution.ExecutionDescription;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.execution.TypedExecutionDescription;
import dev.vexsoft.core.reward.CompiledReward;
import dev.vexsoft.core.reward.CompiledRewards;
import dev.vexsoft.core.reward.PreparedRewards;
import dev.vexsoft.core.reward.Reward;
import dev.vexsoft.core.reward.RewardBehavior;
import dev.vexsoft.core.reward.RewardExecutionReport;
import dev.vexsoft.core.reward.RewardOptions;
import dev.vexsoft.core.reward.RewardResult;
import dev.vexsoft.core.reward.contribution.RewardContribution;
import dev.vexsoft.core.reward.contribution.RewardContributions;
import dev.vexsoft.core.reward.notification.RewardDeliveryObserver;
import dev.vexsoft.core.reward.notification.RewardNotification;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;

/** Default registry-backed reward compiler and executor. */
@Dependencies(ExecutionComponentCoordinatorService.class)
public final class VexRewardService implements RewardService, AutoCloseable {

    private final List<RewardDeliveryObserver> deliveryObservers = new CopyOnWriteArrayList<>();
    private final ExecutionComponentCoordinatorService components;
    private final ObjectMapper transactionMapper = new ObjectMapper().registerModule(new JavaTimeModule())
        .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    /** Captures the shared component registry. */
    public VexRewardService(final VexServiceRegistry services) {
        components = Objects.requireNonNull(services, "services").require(ExecutionComponentCoordinatorService.class);
    }

    @Override
    public AutoCloseable observeDeliveries(final RewardDeliveryObserver observer) {
        deliveryObservers.add(Objects.requireNonNull(observer, "observer"));
        return () -> deliveryObservers.remove(observer);
    }

    @Override
    public void notifyDelivered(final PreparedRewards rewards, final PlayerExecutionContext context) {
        if (deliveryObservers.isEmpty() || rewards.entries().isEmpty()) {
            return;
        }
        // Freeze presentation while the operation still owns its context; never reroll in the observer.
        List<RewardNotification> receipt;
        try {
            receipt = rewards.entries().stream()
                .filter(entry -> entry.reward().getBehavior() == RewardBehavior.ACTION)
                .flatMap(entry -> entry.reward().notifications(context).stream()).toList();
        } catch (RuntimeException failure) {
            // An optional presentation must never change the outcome of an otherwise valid grant.
            System.getLogger(VexRewardService.class.getName()).log(
                System.Logger.Level.ERROR, "Cannot prepare reward presentation", failure
            );
            return;
        }
        if (receipt.isEmpty()) {
            return;
        }
        var profileContexts = context.player().profileContexts();
        context.player().afterCommit(() -> {
            if (!profileContexts.equals(context.player().profileContexts())) {
                return;
            }
            for (RewardDeliveryObserver observer : deliveryObservers) {
                try {
                    observer.delivered(receipt, context);
                } catch (RuntimeException failure) {
                    System.getLogger(VexRewardService.class.getName()).log(
                        System.Logger.Level.ERROR, "Committed reward presentation failed", failure
                    );
                }
            }
        });
    }

    @Override
    public void close() {
        deliveryObservers.clear();
    }

    @Override
    public boolean executeAtomically(final PlayerExecutionContext context, final BooleanSupplier operation) {
        return context.player().atomic(value -> value instanceof TransactionalPlayerData<?> data
            ? value.getClass().cast(Objects.requireNonNull(data.copyForTransaction(), "Transaction snapshot"))
            : transactionMapper.convertValue(value, value.getClass()), operation);
    }

    @Override
    public CompiledRewards compile(final ConfigurationSection section) {
        ConfigurationSection checked = Objects.requireNonNull(section, "section");
        List<CompiledRewards.Entry> entries = new ArrayList<>();

        for (String key : checked.getKeys(false)) {
            Object value = checked.get(key);
            Map<String, Object> envelope = envelope(value);
            String type = envelope == null ? key : Objects.toString(envelope.get("type"), "");
            Reward reward = components.find(ExecutionComponentKind.REWARD, type)
                .map(Reward.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("Unknown reward type: " + type));

            try {
                CompiledReward compiled = reward.compile(envelope == null ? value : envelope.get("value"));
                if (envelope != null) {
                    compiled = new ConfiguredReward(compiled, options(envelope));
                }
                entries.add(new CompiledRewards.Entry(key, compiled));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Invalid reward '" + key + "'", exception);
            }
        }

        return new CompiledRewards(entries);
    }

    @Override
    public RewardExecutionReport grantActions(final CompiledRewards rewards, final PlayerExecutionContext context) {
        return grantPrepared(prepareActions(rewards, context), context);
    }

    @Override
    public PreparedRewards prepareActions(final CompiledRewards rewards, final PlayerExecutionContext context) {
        List<PreparedRewards.Entry> selected = new ArrayList<>();
        for (CompiledRewards.Entry entry : rewards.entries()) {
            CompiledReward reward = entry.reward();
            RewardOptions options = reward.getOptions();
            if (reward.getBehavior() == RewardBehavior.ACTION
                && (options.chance() == 1.0D || ThreadLocalRandom.current().nextDouble() < options.chance())) {
                selected.add(new PreparedRewards.Entry(entry.key(), options, reward.prepare(context)));
            }
        }
        return new PreparedRewards(selected);
    }

    @Override
    public RewardExecutionReport grantPrepared(final PreparedRewards rewards, final PlayerExecutionContext context) {
        Map<String, RewardResult> results = new LinkedHashMap<>();

        for (PreparedRewards.Entry entry : Objects.requireNonNull(rewards, "rewards").entries()) {
            if (entry.reward().getBehavior() == RewardBehavior.ACTION) {
                RewardResult result = entry.reward().grant(context);

                results.put(uniqueResultKey(results, entry.key()), result);

                if (result.status() != RewardResult.Status.SUCCESS) {
                    break;
                }
            }
        }

        var report = new RewardExecutionReport(results);
        if (report.isSuccessful()) {
            notifyDelivered(rewards, context);
        }
        return report;
    }

    private static Map<String, Object> envelope(final Object value) {
        Map<?, ?> raw = value instanceof ConfigurationSection section ? section.getValues(false)
            : value instanceof Map<?, ?> map ? map : Map.of();
        if (!raw.containsKey("type")) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, entry) -> result.put(Objects.toString(key), entry));
        if (!result.containsKey("value") || !(result.get("chance") instanceof Number)) {
            throw new IllegalArgumentException("Named rewards require type, value and numeric chance");
        }
        if (!Set.of("type", "value", "chance", "announce", "announce-type")
            .containsAll(result.keySet())) {
            throw new IllegalArgumentException("Unknown named reward option");
        }
        return result;
    }

    private static RewardOptions options(final Map<String, Object> envelope) {
        Object announce = envelope.getOrDefault("announce", false);
        if (!(announce instanceof Boolean enabled)) {
            throw new IllegalArgumentException("announce must be a boolean");
        }
        String announcement = enabled ? Objects.toString(envelope.get("announce-type"), "") : null;
        return new RewardOptions(((Number) envelope.get("chance")).doubleValue(), announcement);
    }

    private record ConfiguredReward(CompiledReward delegate, RewardOptions options) implements CompiledReward {

        @Override
        public RewardOptions getOptions() {
            return options;
        }

        @Override
        public RewardBehavior getBehavior() {
            return delegate.getBehavior();
        }

        @Override
        public CompiledReward prepare(final PlayerExecutionContext context) {
            return delegate.prepare(context);
        }

        @Override
        public boolean supportsPlayerRollback() {
            return delegate.supportsPlayerRollback();
        }

        @Override
        public boolean changesInventory() {
            return delegate.changesInventory();
        }

        @Override
        public RewardResult grant(final PlayerExecutionContext context) {
            return delegate.grant(context);
        }

        @Override
        public RewardContribution contribute(final PlayerExecutionContext context) {
            return delegate.contribute(context);
        }

        @Override
        public Component describe(final PlayerExecutionContext context) {
            return delegate.describe(context);
        }

        @Override
        public List<ExecutionDescription> describeEntries(
            final PlayerExecutionContext context
        ) {
            return delegate.describeEntries(context);
        }
    }

    private static String uniqueResultKey(final Map<String, RewardResult> results, final String key) {
        if (!results.containsKey(key)) {
            return key;
        }

        int occurrence = 2;

        while (results.containsKey(key + '#' + occurrence)) {
            occurrence++;
        }

        return key + '#' + occurrence;
    }

    @Override
    public RewardContributions calculateContributions(
        final Collection<RewardInvocation> invocations
    ) {
        Map<String, RewardContribution> result = new LinkedHashMap<>();

        for (RewardInvocation invocation : Objects.requireNonNull(invocations, "invocations")) {
            for (CompiledRewards.Entry entry : invocation.rewards().entries()) {
                CompiledReward reward = entry.reward();

                if (reward.getBehavior() != RewardBehavior.CONTRIBUTION
                    || new SplittableRandom(Objects.hash(invocation.context().player().getUniqueId(),
                        invocation.context().variables(), entry.key())).nextDouble() >= reward.getOptions().chance()) {
                    continue;
                }

                RewardContribution contribution = reward.contribute(invocation.context());

                result.merge(entry.key(), contribution, RewardContribution::merge);
            }
        }

        return new RewardContributions(result);
    }

    @Override
    public List<Component> describe(final CompiledRewards rewards, final PlayerExecutionContext context) {
        return rewards.entries().stream().map(entry -> entry.reward().describe(context)).toList();
    }

    @Override
    public List<TypedExecutionDescription> present(
        final CompiledRewards rewards,
        final PlayerExecutionContext context
    ) {
        return rewards.entries()
            .stream()
            .flatMap(entry -> entry.reward()
                .describeEntries(context)
                .stream()
                .map(description -> TypedExecutionDescription.of(entry.key(), description)))
            .toList();
    }
}
