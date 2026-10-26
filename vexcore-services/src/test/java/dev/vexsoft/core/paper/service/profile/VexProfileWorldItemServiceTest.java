package dev.vexsoft.core.paper.service.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.network.ServerId;
import dev.vexsoft.core.api.player.profile.PlayerDataAddress;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.ProfileMutation;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.world.WorldKey;
import dev.vexsoft.core.common.data.MemoryPlayerDataStore;
import dev.vexsoft.core.common.service.cache.VexCacheService;
import dev.vexsoft.core.common.service.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.paper.profile.ProfileItemRealm;
import dev.vexsoft.core.paper.service.network.ServerIdentityService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.service.world.WorldService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

/** Tests durable world ownership with real document CAS and independent game and database executors. */
class VexProfileWorldItemServiceTest {

    private static final ProfileDomain DOMAIN = new ProfileDomain("test:game");

    @Test
    void publicPickupLosingAdmissionToAnotherProfileActionCanRetryWithoutLosingOrDuplicatingLoot() throws Exception {
        try (var fixture = new Fixture()) {
            var owner = fixture.owner(UUID.randomUUID(), 0, 64);
            var item = fixture.item(UUID.randomUUID(), new Stack(5));
            fixture.rejectNextTransactionBusy = true;
            fixture.entity.submit(() -> fixture.service.collect(owner.player, item.platform)).get()
                .get(5, TimeUnit.SECONDS);
            assertEquals(0, fixture.entity.submit(owner::amount).get());
            assertFalse(item.removed);
            assertEquals(5, item.stack.getAmount());
            assertFalse(fixture.commitReached.isDone());

            TimeUnit.MILLISECONDS.sleep(300);
            fixture.entity.submit(() -> fixture.service.collect(owner.player, item.platform)).get()
                .get(5, TimeUnit.SECONDS);
            assertEquals(5, fixture.entity.submit(owner::amount).get());
            assertTrue(item.removed);
        }
    }

    @Test
    void journalPickupLosingAdmissionRetainsDurableDropUntilSuccessfulRetry() throws Exception {
        try (var fixture = new Fixture()) {
            var owner = fixture.owner(UUID.randomUUID(), 8, 64);
            fixture.entity.submit(() -> fixture.service.drop(owner.player, List.of(new Stack(3)), fixture.location))
                .get().get(5, TimeUnit.SECONDS);
            var journal = fixture.journal(owner);
            var before = fixture.memory.loadProfileDocuments(List.of(journal)).join().get(journal);
            var entry = new ObjectMapper().readTree(before.value()).get("drops").properties().iterator().next();
            UUID dropId = UUID.fromString(entry.getKey());
            var stack = Stack.decode(java.util.Base64.getDecoder().decode(entry.getValue().get("stack").asText()));
            var item = fixture.item(UUID.randomUUID(), stack);
            fixture.entity.submit(() -> item.platform.getPersistentDataContainer().set(
                new NamespacedKey("vexcore", "profile_world_drop"), PersistentDataType.STRING, dropId.toString())).get();

            fixture.rejectNextTransactionBusy = true;
            fixture.entity.submit(() -> fixture.service.collect(owner.player, item.platform)).get()
                .get(5, TimeUnit.SECONDS);
            assertEquals(5, fixture.entity.submit(owner::amount).get());
            assertFalse(item.removed);
            assertEquals(before, fixture.memory.loadProfileDocuments(List.of(journal)).join().get(journal));

            TimeUnit.MILLISECONDS.sleep(300);
            fixture.entity.submit(() -> fixture.service.collect(owner.player, item.platform)).get()
                .get(5, TimeUnit.SECONDS);
            assertEquals(8, fixture.entity.submit(owner::amount).get());
            assertTrue(item.removed);
            var after = fixture.memory.loadProfileDocuments(List.of(journal)).join().get(journal);
            assertTrue(new ObjectMapper().readTree(after.value()).get("drops").isEmpty());
        }
    }

    @Test
    void pickupCommitFailureStillPropagatesAndRetainsNativeSource() throws Exception {
        try (var fixture = new Fixture()) {
            var owner = fixture.owner(UUID.randomUUID(), 0, 64);
            var item = fixture.item(UUID.randomUUID(), new Stack(5));
            fixture.rejectCommit = true;
            var operation = fixture.entity.submit(() -> fixture.service.collect(owner.player, item.platform)).get();
            assertThrows(ExecutionException.class, () -> operation.get(5, TimeUnit.SECONDS));
            assertEquals(0, fixture.entity.submit(owner::amount).get());
            assertFalse(item.removed);
            assertEquals(5, item.stack.getAmount());
        }
    }

    @Test
    void failedDropCommitRestoresNativeSource() throws Exception {
        try (var fixture = new Fixture()) {
            var owner = fixture.owner(UUID.randomUUID(), 8, 64);
            fixture.rejectCommit = true;
            var operation = fixture.entity.submit(() -> fixture.service.drop(owner.player,
                List.of(new Stack(3)), fixture.location)).get();

            assertThrows(ExecutionException.class, () -> operation.get(5, TimeUnit.SECONDS));
            assertEquals(8, fixture.entity.submit(owner::amount).get());
            assertTrue(fixture.memory.loadProfileDocuments(List.of(fixture.journal(owner))).join().isEmpty());
        }
    }

    @Test
    void simultaneousCoopDropsCannotOverwriteOrLoseEitherSource() throws Exception {
        try (var fixture = new Fixture()) {
            UUID profile = UUID.randomUUID();
            var first = fixture.owner(profile, 3, 64);
            var second = fixture.owner(profile, 4, 64);
            fixture.commitGate = new CompletableFuture<>();
            var firstDrop = fixture.entity.submit(() -> fixture.service.drop(first.player,
                List.of(new Stack(3)), fixture.location)).get();
            fixture.commitReached.get(5, TimeUnit.SECONDS);
            var secondDrop = fixture.entity.submit(() -> fixture.service.drop(second.player,
                List.of(new Stack(4)), fixture.location)).get();
            fixture.secondCommitReached.get(5, TimeUnit.SECONDS);
            fixture.commitGate.complete(null);

            var firstResult = firstDrop.handle((ignored, failure) -> failure == null).get(5, TimeUnit.SECONDS);
            var secondResult = secondDrop.handle((ignored, failure) -> failure == null).get(5, TimeUnit.SECONDS);
            assertTrue(firstResult ^ secondResult);
            int remaining = fixture.entity.submit(() -> first.amount() + second.amount()).get();
            var stored = fixture.memory.loadProfileDocuments(List.of(fixture.journal(first))).join();
            var drops = new ObjectMapper().readTree(stored.get(fixture.journal(first)).value()).get("drops");
            assertEquals(1, drops.size());
            String encoded = drops.elements().next().get("stack").asText();
            int dropped = Stack.decode(java.util.Base64.getDecoder().decode(encoded)).getAmount();
            assertEquals(7, remaining + dropped);
        }
    }

    @Test
    void claimedPublicLootCannotPayAgainAfterServiceRestartOrStaleEntityReplay() throws Exception {
        try (var fixture = new Fixture()) {
            var owner = fixture.owner(UUID.randomUUID(), 0, 64);
            UUID source = UUID.randomUUID();
            var item = fixture.item(source, new Stack(5));
            fixture.entity.submit(() -> fixture.service.collect(owner.player, item.platform)).get()
                .get(5, TimeUnit.SECONDS);
            assertEquals(5, fixture.entity.submit(owner::amount).get());
            assertTrue(item.removed);

            fixture.restart();
            var staleEntity = fixture.item(source, new Stack(777));
            fixture.entity.submit(() -> fixture.service.collect(owner.player, staleEntity.platform)).get()
                .get(5, TimeUnit.SECONDS);
            assertEquals(5, fixture.entity.submit(owner::amount).get());
            assertTrue(staleEntity.removed);
        }
    }

    @Test
    void partialPublicPickupRetainsOnlyTheUnpaidAmountForAnotherProfile() throws Exception {
        try (var fixture = new Fixture()) {
            var first = fixture.owner(UUID.randomUUID(), 0, 2);
            UUID source = UUID.randomUUID();
            var item = fixture.item(source, new Stack(5));
            fixture.entity.submit(() -> fixture.service.collect(first.player, item.platform)).get()
                .get(5, TimeUnit.SECONDS);
            assertEquals(2, fixture.entity.submit(first::amount).get());
            assertFalse(item.removed);
            assertEquals(3, item.stack.getAmount());
            assertEquals(null, ProfileItemRealm.realm(item.stack));

            fixture.restart();
            var second = fixture.owner(UUID.randomUUID(), 0, 64);
            var staleEntity = fixture.item(source, new Stack(5));
            fixture.entity.submit(() -> fixture.service.collect(second.player, staleEntity.platform)).get()
                .get(5, TimeUnit.SECONDS);
            assertEquals(3, fixture.entity.submit(second::amount).get());
            assertTrue(staleEntity.removed);
            assertFalse(ProfileItemRealm.permits(second.contents[0], first.context));
            assertTrue(ProfileItemRealm.permits(second.contents[0], second.context));
        }
    }

    @Test
    void fullInventoryRejectsPickupWithoutBeginningAStorageTransaction() throws Exception {
        try (var fixture = new Fixture()) {
            var owner = fixture.owner(UUID.randomUUID(), 64, 64);
            for (int slot = 0; slot < 36; slot++) {
                owner.contents[slot] = new Stack(64);
            }
            var item = fixture.item(UUID.randomUUID(), new Stack(5));
            fixture.entity.submit(() -> fixture.service.collect(owner.player, item.platform)).get()
                .get(5, TimeUnit.SECONDS);
            assertFalse(fixture.commitReached.isDone());
            assertFalse(item.removed);
            assertFalse(fixture.entity.submit(() -> fixture.service.isProjection(item.platform)).get());
            assertEquals(5, item.stack.getAmount());
        }
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static PersistentDataContainer data(final Map<NamespacedKey, Object> values) {
        return proxy(PersistentDataContainer.class, (object, method, arguments) -> switch (method.getName()) {
            case "set" -> {
                values.put((NamespacedKey) arguments[0], arguments[2]);
                yield null;
            }
            case "get" -> values.get(arguments[0]);
            case "has" -> values.containsKey(arguments[0]);
            case "getKeys" -> values.keySet();
            default -> null;
        });
    }

    private static final class Fixture implements AutoCloseable {

        private final ExecutorService entity = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "world-item-test-entity"));
        private final ExecutorService database = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "world-item-test-database"));
        private final MemoryPlayerDataStore memory = new MemoryPlayerDataStore();
        private final Map<Class<?>, Object> dependencies = new HashMap<>();
        private final Map<VexPlayer, Owner> owners = new HashMap<>();
        private final World world = proxy(World.class, (object, method, arguments) -> null);
        private final Location location = new Location(world, 0, 80, 0);
        private final VexServiceRegistry registry;
        private final CompletableFuture<Void> commitReached = new CompletableFuture<>();
        private final CompletableFuture<Void> secondCommitReached = new CompletableFuture<>();
        private CompletableFuture<Void> commitGate = CompletableFuture.completedFuture(null);
        private boolean rejectCommit;
        private boolean rejectNextTransactionBusy;
        private VexProfileWorldItemService service;

        @SuppressWarnings("unchecked")
        private Fixture() {
            var owner = proxy(ServiceOwner.class, (object, method, arguments) -> "test");
            registry = proxy(VexServiceRegistry.class, (object, method, arguments) ->
                method.getName().equals("getOwner") ? owner : dependencies.get(arguments[0]));
            dependencies.put(CacheService.class, new VexCacheService(registry));
            dependencies.put(ProfileStorageService.class, proxy(ProfileStorageService.class,
                (object, method, arguments) -> switch (method.getName()) {
                    case "load" -> CompletableFuture.supplyAsync(() -> {
                        assertEquals("world-item-test-database", Thread.currentThread().getName());
                        return memory.loadProfileDocuments((Collection<ProfileDocumentKey>) arguments[0]).join();
                    }, database);
                    case "databaseTime" -> CompletableFuture.supplyAsync(System::currentTimeMillis, database);
                    case "commit" -> {
                        if (!commitReached.complete(null)) {
                            secondCommitReached.complete(null);
                        }
                        yield commitGate.thenComposeAsync(ignored -> rejectCommit
                            ? CompletableFuture.completedFuture(false)
                            : memory.commitProfileDocuments((Collection<ProfileDocumentChange>) arguments[0]), database);
                    }
                    default -> null;
                }));
            dependencies.put(ProfileSessionService.class, proxy(ProfileSessionService.class,
                (object, method, arguments) -> switch (method.getName()) {
                    case "activeContext" -> Optional.of(owners.get(arguments[0]).context);
                    case "isReady", "persistItem" -> true;
                    case "transact" -> {
                        if (rejectNextTransactionBusy) {
                            rejectNextTransactionBusy = false;
                            yield CompletableFuture.failedFuture(new CompletionException(new ProfileSessionBusyException()));
                        }
                        yield transact(owners.get(arguments[0]), (Supplier<ProfileMutation<?>>) arguments[1]);
                    }
                    default -> null;
                }));
            dependencies.put(PlayerDataCoordinatorService.class, proxy(PlayerDataCoordinatorService.class,
                (object, method, arguments) -> owners.keySet()));
            dependencies.put(ScheduleService.class, proxy(ScheduleService.class, (object, method, arguments) -> {
                if (method.getName().equals("runFor")) {
                    entity.submit((Runnable) arguments[1]).get();
                    return Optional.empty();
                }
                // Projection rendering is covered by server acceptance, not emulated Minecraft NBT.
                return null;
            }));
            dependencies.put(WorldService.class, proxy(WorldService.class,
                (object, method, arguments) -> WorldKey.parse("minecraft:overworld")));
            dependencies.put(ServerIdentityService.class, proxy(ServerIdentityService.class,
                (object, method, arguments) -> new ServerId("test")));
            service = new VexProfileWorldItemService(registry, Stack::decode);
        }

        private Owner owner(final UUID profile, final int amount, final int capacity) {
            var owner = new Owner(profile, amount, capacity, world, location);
            owners.put(owner.player, owner);
            return owner;
        }

        private NativeItem item(final UUID id, final Stack stack) {
            return new NativeItem(id, stack, world, location);
        }

        private ProfileDocumentKey journal(final Owner owner) {
            return new ProfileDocumentKey(PlayerDataAddress.shared(DOMAIN, owner.context.profileId()),
                "vexcore", "world_items");
        }

        private CompletableFuture<Object> transact(final Owner owner, final Supplier<ProfileMutation<?>> operation) {
            var before = new ItemStack[owner.contents.length];
            return CompletableFuture.supplyAsync(() -> {
                assertEquals("world-item-test-entity", Thread.currentThread().getName());
                for (int index = 0; index < before.length; index++) {
                    before[index] = owner.contents[index] == null ? null : owner.contents[index].clone();
                }
                owner.player.beginDurableEffects();
                return operation.get();
            }, entity).thenCompose(mutation -> {
                var storage = (ProfileStorageService) dependencies.get(ProfileStorageService.class);
                return storage.commit(mutation.changes()).thenApplyAsync(committed -> {
                    if (!committed) {
                        owner.contents = before;
                    }
                    owner.player.finishDurableEffects(committed);
                    if (!committed) {
                        throw new IllegalStateException("World ownership CAS conflict");
                    }
                    return mutation.result();
                }, entity);
            });
        }

        private void restart() {
            service.close();
            ((CacheService) dependencies.get(CacheService.class)).destroy("profile_world_item_attempts");
            service = new VexProfileWorldItemService(registry, Stack::decode);
        }

        @Override
        public void close() {
            service.close();
            entity.close();
            database.close();
        }
    }

    private static final class Owner {

        private final VexPlayer player = new VexPlayer(UUID.randomUUID(), "Alex");
        private final PlayerProfileContext context;
        private ItemStack[] contents = new ItemStack[43];

        private Owner(final UUID profile, final int amount, final int capacity, final World world, final Location location) {
            context = new PlayerProfileContext(DOMAIN, profile, player.getUniqueId(), UUID.randomUUID(), 1, 1, 1);
            player.installProfile(context, Map.of());
            contents[0] = new Stack(amount);
            var inventory = proxy(PlayerInventory.class, (object, method, arguments) -> {
                assertEquals("world-item-test-entity", Thread.currentThread().getName());
                return switch (method.getName()) {
                    case "getContents" -> contents;
                    case "getStorageContents" -> java.util.Arrays.copyOf(contents, 36);
                    case "getMaxStackSize" -> 64;
                    case "setContents" -> {
                        contents = (ItemStack[]) arguments[0];
                        yield null;
                    }
                    case "addItem" -> {
                        var stack = ((ItemStack[]) arguments[0])[0];
                        int granted = Math.min(stack.getAmount(), Math.max(0, capacity - amount()));
                        int remainder = stack.getAmount() - granted;
                        ItemStack acquired = stack.clone();
                        acquired.setAmount(amount() + granted);
                        contents[0] = acquired;
                        stack.setAmount(remainder);
                        yield new HashMap<>(remainder == 0 ? Map.of() : Map.of(0, stack));
                    }
                    default -> null;
                };
            });
            player.bindPlatformPlayer(proxy(Player.class, (object, method, arguments) -> {
                assertEquals("world-item-test-entity", Thread.currentThread().getName());
                return switch (method.getName()) {
                    case "getInventory" -> inventory;
                    case "getWorld" -> world;
                    case "getLocation" -> location;
                    default -> null;
                };
            }));
        }

        private int amount() {
            return contents[0] == null ? 0 : contents[0].getAmount();
        }
    }

    private static final class NativeItem {

        private final Item platform;
        private Stack stack;
        private volatile boolean removed;

        private NativeItem(final UUID id, final Stack stack, final World world, final Location location) {
            this.stack = stack;
            var data = data(new HashMap<>());
            platform = proxy(Item.class, (object, method, arguments) -> {
                assertEquals("world-item-test-entity", Thread.currentThread().getName());
                return switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getPersistentDataContainer" -> data;
                    case "getItemStack" -> this.stack;
                    case "setTicksLived" -> {
                        if ((int) arguments[0] <= 0) {
                            throw new IllegalArgumentException("Age must be greater than 0");
                        }
                        yield null;
                    }
                    case "setItemStack" -> {
                        this.stack = (Stack) arguments[0];
                        yield null;
                    }
                    case "isValid" -> !removed;
                    case "getWorld" -> world;
                    case "getLocation" -> location;
                    case "remove" -> {
                        removed = true;
                        yield null;
                    }
                    default -> null;
                };
            });
        }
    }

    private static final class Stack extends ItemStack {

        private int amount;
        private final Map<NamespacedKey, Object> values = new HashMap<>();

        private Stack(final int amount) {
            this.amount = amount;
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public void setAmount(final int amount) {
            this.amount = amount;
        }

        @Override
        public boolean isEmpty() {
            return amount == 0;
        }

        @Override
        public boolean isSimilar(final ItemStack other) {
            return other instanceof Stack stack && values.equals(stack.values);
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof Stack stack && amount == stack.amount && values.equals(stack.values);
        }

        @Override
        public int hashCode() {
            return 31 * amount + values.hashCode();
        }

        @Override
        public Stack clone() {
            var copy = new Stack(amount);
            copy.values.putAll(values);
            return copy;
        }

        @Override
        public PersistentDataContainer getPersistentDataContainer() {
            return data(values);
        }

        @Override
        public boolean editMeta(final Consumer<? super ItemMeta> operation) {
            operation.accept(proxy(ItemMeta.class, (object, method, arguments) -> data(values)));
            return true;
        }

        @Override
        public byte[] serializeAsBytes() {
            return (amount + ";" + values.getOrDefault(new NamespacedKey("vexcore", "profile_item_domain"), "")
                + ";" + values.getOrDefault(new NamespacedKey("vexcore", "profile_item_id"), ""))
                .getBytes(StandardCharsets.UTF_8);
        }

        private static Stack decode(final byte[] bytes) {
            String[] parts = new String(bytes, StandardCharsets.UTF_8).split(";", -1);
            var stack = new Stack(Integer.parseInt(parts[0]));
            if (!parts[1].isEmpty()) {
                stack.values.put(new NamespacedKey("vexcore", "profile_item_domain"), parts[1]);
                stack.values.put(new NamespacedKey("vexcore", "profile_item_id"), parts[2]);
            }
            return stack;
        }
    }
}
