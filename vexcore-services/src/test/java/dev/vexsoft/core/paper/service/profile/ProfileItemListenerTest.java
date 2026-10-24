package dev.vexsoft.core.paper.service.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.profile.ProfileItemRealm;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

/** Verifies native drop sources and pickups without scheduling, storage or inventory reconstruction. */
class ProfileItemListenerTest {

    private static final ProfileDomain DOMAIN = new ProfileDomain("test:game");

    @Test
    void creativePlayersCanOpenPhysicalContainersWhileOtherProfileModesRemainProtected() {
        for (var mode : GameMode.values()) {
            var fixture = new Fixture();
            fixture.mode = mode;
            var container = proxy(Container.class, (object, method, arguments) -> null);
            var block = proxy(Block.class, (object, method, arguments) -> container);
            var interact = new PlayerInteractEvent(fixture.platform, Action.RIGHT_CLICK_BLOCK, null, block,
                org.bukkit.block.BlockFace.UP);
            fixture.listener.container(interact);
            assertEquals(mode != GameMode.CREATIVE, interact.isCancelled(), mode.name());

            for (var holder : new InventoryHolder[]{container,
                proxy(StorageMinecart.class, (object, method, arguments) -> null)}) {
                var inventory = proxy(Inventory.class, (object, method, arguments) -> holder);
                var view = proxy(InventoryView.class, (object, method, arguments) -> switch (method.getName()) {
                    case "getPlayer" -> fixture.platform;
                    case "getTopInventory" -> inventory;
                    default -> throw new AssertionError(method.getName());
                });
                var open = new InventoryOpenEvent(view);
                fixture.listener.openPhysicalContainer(open);
                assertEquals(mode != GameMode.CREATIVE, open.isCancelled(), mode.name());
            }

            var minecart = proxy(StorageMinecart.class, (object, method, arguments) -> null);
            var entity = new PlayerInteractEntityEvent(fixture.platform, minecart);
            fixture.listener.itemFrame(entity);
            assertEquals(mode != GameMode.CREATIVE, entity.isCancelled(), mode.name());
        }
    }

    @Test
    void singleAndWholeStackDropsLeaveTheNativeSourceRemovalUntouched() {
        for (int amount : new int[]{1, 64}) {
            var fixture = new Fixture();
            var original = fixture.stack;
            original.setAmount(amount);
            var event = new PlayerDropItemEvent(fixture.platform, fixture.item);

            fixture.listener.drop(event);
            assertFalse(event.isCancelled(), "Cancelling Vanilla loses the exact source slot");
            fixture.listener.scopeDrop(event);

            assertEquals(amount, original.getAmount());
            assertNull(ProfileItemRealm.realm(original));
            assertEquals(amount, fixture.stack.getAmount());
            assertEquals(DOMAIN.value() + "/" + fixture.context.profileId(), ProfileItemRealm.realm(fixture.stack));
            assertFalse(fixture.mobPickup);
            assertEquals(0, fixture.collections);
        }
    }

    @Test
    void droppedItemMetadataIsAppliedAfterOtherPluginsCanCancelTheNativeDrop() throws Exception {
        var handler = ProfileItemListener.class.getMethod("scopeDrop", PlayerDropItemEvent.class)
            .getAnnotation(org.bukkit.event.EventHandler.class);
        assertEquals(org.bukkit.event.EventPriority.MONITOR, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    @Test
    void ordinaryPublicPickupRemainsNativeAndDoesNotBindItsPartialRemainder() {
        var fixture = new Fixture();
        var event = new EntityPickupItemEvent(fixture.platform, fixture.item, 3);

        fixture.listener.pickup(event);

        assertFalse(event.isCancelled());
        assertEquals(5, fixture.stack.getAmount());
        assertNull(ProfileItemRealm.realm(fixture.stack));
        assertEquals(0, fixture.collections);
    }

    @Test
    void nativeDropsFromTheCurrentProfileCanBePickedUpWithoutAJournalTransaction() {
        var fixture = new Fixture();
        ProfileItemRealm.bind(fixture.stack, fixture.context);
        var event = new EntityPickupItemEvent(fixture.platform, fixture.item, 0);

        fixture.listener.pickup(event);

        assertFalse(event.isCancelled());
        assertEquals(0, fixture.collections);
    }

    @Test
    void anotherProfilesPossessionsCannotBeDroppedOrPickedUp() {
        var fixture = new Fixture();
        var other = new PlayerProfileContext(DOMAIN, UUID.randomUUID(), fixture.owner.getUniqueId(),
            UUID.randomUUID(), 1, 1, 1);
        ProfileItemRealm.bind(fixture.stack, other);
        var drop = new PlayerDropItemEvent(fixture.platform, fixture.item);
        var pickup = new EntityPickupItemEvent(fixture.platform, fixture.item, 0);

        fixture.listener.drop(drop);
        fixture.listener.pickup(pickup);

        assertTrue(drop.isCancelled());
        assertTrue(pickup.isCancelled());
        assertEquals(0, fixture.collections);
    }

    @Test
    void existingJournalProjectionsStillSettleTheirRecordedOwnership() {
        var fixture = new Fixture();
        fixture.projection = true;
        ProfileItemRealm.bind(fixture.stack, fixture.context);
        var event = new EntityPickupItemEvent(fixture.platform, fixture.item, 0);

        fixture.listener.pickup(event);

        assertTrue(event.isCancelled());
        assertEquals(1, fixture.collections);
    }

    @Test
    void accountOnlyPlayersKeepNativePublicDropsAndPickupsButCannotCollectScopedItems() {
        var fixture = new Fixture();
        fixture.participating = false;
        var drop = new PlayerDropItemEvent(fixture.platform, fixture.item);
        var pickup = new EntityPickupItemEvent(fixture.platform, fixture.item, 0);

        fixture.listener.drop(drop);
        fixture.listener.scopeDrop(drop);
        fixture.listener.pickup(pickup);

        assertFalse(drop.isCancelled());
        assertFalse(pickup.isCancelled());
        assertNull(ProfileItemRealm.realm(fixture.stack));

        ProfileItemRealm.bind(fixture.stack, fixture.context);
        var scoped = new EntityPickupItemEvent(fixture.platform, fixture.item, 0);
        fixture.listener.pickup(scoped);
        assertTrue(scoped.isCancelled());
        assertEquals(0, fixture.collections);
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
            default -> throw new AssertionError(method.getName());
        });
    }

    private static final class Fixture {

        private final VexPlayer owner = new VexPlayer(UUID.randomUUID(), "Alex");
        private final PlayerProfileContext context = new PlayerProfileContext(DOMAIN, UUID.randomUUID(),
            owner.getUniqueId(), UUID.randomUUID(), 1, 1, 1);
        private final Player platform;
        private final Item item;
        private final ProfileItemListener listener;
        private Stack stack = new Stack(5);
        private boolean mobPickup = true;
        private boolean projection;
        private boolean participating = true;
        private GameMode mode = GameMode.ADVENTURE;
        private int collections;

        private Fixture() {
            platform = proxy(Player.class, (object, method, arguments) -> {
                if (method.getName().equals("getGameMode")) {
                    return mode;
                }
                if (method.getName().equals("getUniqueId")) {
                    return owner.getUniqueId();
                }
                throw new AssertionError("Native event must retain its source inventory: " + method.getName());
            });
            item = proxy(Item.class, (object, method, arguments) -> switch (method.getName()) {
                case "getItemStack" -> stack;
                case "setItemStack" -> {
                    stack = (Stack) arguments[0];
                    yield null;
                }
                case "setCanMobPickup" -> {
                    mobPickup = (boolean) arguments[0];
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            });
            var players = proxy(PlayerService.class, (object, method, arguments) -> {
                if (method.getName().equals("find")) {
                    return Optional.of(owner);
                }
                throw new AssertionError(method.getName());
            });
            var sessions = proxy(ProfileSessionService.class, (object, method, arguments) -> {
                if (method.getName().equals("activeContext")) {
                    return participating ? Optional.of(context) : Optional.empty();
                }
                throw new AssertionError("Native item movement must not transact: " + method.getName());
            });
            var worldItems = proxy(ProfileWorldItemService.class, (object, method, arguments) -> switch (method.getName()) {
                case "isProjection" -> projection;
                case "collect" -> {
                    assertTrue(projection, "Only an existing journal projection may enter persistence");
                    assertEquals(owner, arguments[0]);
                    assertTrue(item == arguments[1]);
                    collections++;
                    yield CompletableFuture.completedFuture(null);
                }
                default -> throw new AssertionError("Native drops must not search inventory or write: " + method.getName());
            });
            var schedules = proxy(ScheduleService.class, (object, method, arguments) -> {
                throw new AssertionError("Native item movement must not be delayed: " + method.getName());
            });
            Map<Class<?>, Object> services = Map.of(PlayerService.class, players, ProfileSessionService.class, sessions,
                ProfileWorldItemService.class, worldItems, ScheduleService.class, schedules);
            listener = new ProfileItemListener(proxy(VexServiceRegistry.class, (object, method, arguments) ->
                services.get(arguments[0])));
        }
    }

    @AllArgsConstructor
    private static final class Stack extends ItemStack {

        private final Map<NamespacedKey, Object> values = new HashMap<>();
        @Getter
        @Setter
        private int amount;

        @Override
        public boolean isEmpty() {
            return amount == 0;
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
    }
}
