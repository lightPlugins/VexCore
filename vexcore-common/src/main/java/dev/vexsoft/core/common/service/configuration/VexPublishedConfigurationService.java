package dev.vexsoft.core.common.service.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.configuration.ConfigurationOwner;
import dev.vexsoft.core.api.service.configuration.PublishedConfigurationService;
import dev.vexsoft.core.api.service.configuration.PublishedConfigurationVersion;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.data.global.GlobalDataReference;
import dev.vexsoft.core.common.data.global.GlobalDataStore;
import dev.vexsoft.core.common.data.global.StoredGlobalData;
import dev.vexsoft.core.common.service.data.PlayerDataStoreService;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Database-backed published configuration service scoped to one plugin owner. */
@Dependencies(PlayerDataStoreService.class)
public final class VexPublishedConfigurationService implements PublishedConfigurationService, AutoCloseable {

    private static final String KEY_PREFIX = "published_";

    private final String owner;
    private final ConfigurationOwner configurationOwner;
    private final GlobalDataStore store;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Class<?>, Registration<?>> registrations = new ConcurrentHashMap<>();
    private final AutoCloseable subscription;

    public VexPublishedConfigurationService(final VexServiceRegistry services) {
        VexServiceRegistry checked = Objects.requireNonNull(services, "services");
        owner = checked.getOwner().getServiceOwnerName();
        configurationOwner = checked.getOwner() instanceof ConfigurationOwner supported ? supported : null;
        store = checked.require(PlayerDataStoreService.class).getGlobalStore();
        subscription = store.subscribeGlobalDataChanges(this::refresh);
    }

    @Override
    public synchronized <T> void register(final Class<T> type, final String system) {
        Class<T> checkedType = Objects.requireNonNull(type, "type");
        String checkedSystem = Objects.requireNonNull(system, "system");

        if (!checkedSystem.matches("[a-z][a-z0-9_]{0,52}")) {
            throw new IllegalArgumentException("Invalid published configuration system: " + checkedSystem);
        }

        if (registrations.containsKey(checkedType)) {
            throw new IllegalStateException("Published configuration type is already registered: " + checkedType);
        }

        String key = KEY_PREFIX + checkedSystem;

        if (registrations.values().stream().anyMatch(registration -> registration.key.equals(key))) {
            throw new IllegalStateException("Published configuration system is already registered: " + checkedSystem);
        }

        Registration<T> registration = new Registration<>(checkedType, key);
        store.loadGlobalData(owner, key).join().ifPresent(stored -> registration.install(read(registration, stored), stored.revision()));
        registrations.put(checkedType, registration);
    }

    @Override
    public boolean isRegistered(final Class<?> type) {
        return registrations.containsKey(Objects.requireNonNull(type, "type"));
    }

    @Override
    public <T> Optional<T> find(final Class<T> type) {
        return Optional.ofNullable(registration(type).value);
    }

    @Override
    public <T> T require(final Class<T> type) {
        return find(type).orElseThrow(() -> new IllegalStateException(
            "Published configuration is not loaded for " + owner + ": " + type.getSimpleName()));
    }

    @Override
    public <T> CompletableFuture<PublishedConfigurationVersion> publish(
        final Class<T> type,
        final T value,
        final String commit
    ) {
        Registration<T> registration = registration(type);
        T checkedValue = type.cast(Objects.requireNonNull(value, "value"));
        String checkedCommit = Objects.requireNonNull(commit, "commit");

        if (!checkedCommit.matches("[0-9a-fA-F]{40,64}")) {
            throw new IllegalArgumentException("Source commit must be a Git commit hash");
        }

        String json;

        try {
            json = mapper.writeValueAsString(new Payload(checkedCommit, mapper.valueToTree(checkedValue)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to serialize published configuration", exception);
        }

        return publish(registration, checkedValue, checkedCommit, json, 0);
    }

    @Override
    public <T> AutoCloseable subscribe(final Class<T> type, final Consumer<T> listener) {
        Registration<T> registration = registration(type);
        Consumer<T> checkedListener = Objects.requireNonNull(listener, "listener");
        registration.listeners.add(checkedListener);

        return () -> registration.listeners.remove(checkedListener);
    }

    @Override
    public void close() throws Exception {
        subscription.close();
        registrations.clear();
    }

    private <T> CompletableFuture<PublishedConfigurationVersion> publish(
        final Registration<T> registration,
        final T value,
        final String commit,
        final String json,
        final int attempt
    ) {
        return store.loadGlobalData(owner, registration.key).thenCompose(current -> {
            long revision = current.map(StoredGlobalData::revision).orElse(0L);

            if (current.isPresent() && read(registration, current.orElseThrow()).commit.equals(commit)) {
                return CompletableFuture.completedFuture(new PublishedConfigurationVersion(commit, revision, false));
            }

            return store.compareAndSetGlobalData(owner, registration.key, revision, json).thenCompose(stored -> {
                if (stored.isPresent()) {
                    registration.install(new Payload(commit, mapper.valueToTree(value)), stored.orElseThrow().revision());

                    return CompletableFuture.completedFuture(
                        new PublishedConfigurationVersion(commit, stored.orElseThrow().revision(), true));
                }

                if (attempt >= 15) {
                    return CompletableFuture.failedFuture(new IllegalStateException(
                        "Published configuration changed too frequently: " + registration.key));
                }

                return publish(registration, value, commit, json, attempt + 1);
            });
        });
    }

    private void refresh(final GlobalDataReference reference) {
        if (!owner.equals(reference.owner())) {
            return;
        }

        registrations.values().stream()
            .filter(registration -> registration.key.equals(reference.key()))
            .findFirst()
            .ifPresent(this::reload);
    }

    private <T> void reload(final Registration<T> registration) {
        store.loadGlobalData(owner, registration.key).thenAccept(stored -> {
            if (stored.isPresent() && stored.orElseThrow().revision() > registration.revision) {
                StoredGlobalData current = stored.orElseThrow();
                registration.install(read(registration, current), current.revision());
            }
        }).exceptionally(error -> {
            if (configurationOwner != null) {
                configurationOwner.reportConfigurationWarning(
                    "Unable to refresh published configuration " + registration.key,
                    error
                );
            }

            return null;
        });
    }

    private <T> Payload read(final Registration<T> registration, final StoredGlobalData stored) {
        try {
            JsonNode root = mapper.readTree(stored.value());
            String commit = root.path("commit").asText();
            JsonNode data = root.required("data");
            registration.type.cast(mapper.treeToValue(data, registration.type));

            return new Payload(commit, data);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid stored configuration " + owner + ':' + registration.key, exception);
        }
    }

    private <T> Registration<T> registration(final Class<T> type) {
        @SuppressWarnings("unchecked")
        Registration<T> registration = (Registration<T>) registrations.get(Objects.requireNonNull(type, "type"));

        if (registration == null) {
            throw new IllegalArgumentException("Published configuration type is not registered: " + type);
        }

        return registration;
    }

    private record Payload(String commit, JsonNode data) {
    }

    private final class Registration<T> {

        private final Class<T> type;
        private final String key;
        private final CopyOnWriteArrayList<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
        private volatile T value;
        private volatile long revision;

        private Registration(final Class<T> type, final String key) {
            this.type = type;
            this.key = key;
        }

        private synchronized void install(final Payload payload, final long newRevision) {
            if (newRevision <= revision) {
                return;
            }

            T loaded;

            try {
                loaded = mapper.treeToValue(payload.data, type);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to decode published configuration " + key, exception);
            }

            value = loaded;
            revision = newRevision;
            listeners.forEach(listener -> listener.accept(loaded));
        }
    }
}
