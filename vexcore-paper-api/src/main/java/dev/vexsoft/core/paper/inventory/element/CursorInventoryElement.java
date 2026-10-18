package dev.vexsoft.core.paper.inventory.element;

import dev.vexsoft.core.paper.inventory.InventoryContext;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import org.bukkit.inventory.ItemStack;

/** A rendered slot backed by an external item store, including empty-slot placeholders. */
public final class CursorInventoryElement extends RefreshableInventoryElement {

    private final Function<InventoryContext, CompletableFuture<?>> transfer;

    /**
     * The callback validates the cursor and atomically exchanges it with its backing store. Rejection
     * must leave the cursor and store unchanged. The framework never transfers the rendered icon and
     * refreshes the menu after the callback returns.
     */
    public CursorInventoryElement(Function<InventoryContext, ItemStack> renderer, Consumer<InventoryContext> transfer) {
        super(renderer);
        Objects.requireNonNull(transfer, "transfer");
        this.transfer = context -> {
            transfer.accept(context);
            return CompletableFuture.completedFuture(null);
        };
    }

    private CursorInventoryElement(Function<InventoryContext, ItemStack> renderer,
        Function<InventoryContext, CompletableFuture<?>> transfer) {
        super(renderer);
        this.transfer = Objects.requireNonNull(transfer, "transfer");
    }

    /** Defers the framework's single refresh until the backing store's durable exchange has settled. */
    public static CursorInventoryElement asynchronous(Function<InventoryContext, ItemStack> renderer,
        Function<InventoryContext, CompletableFuture<?>> transfer) {
        return new CursorInventoryElement(renderer, transfer);
    }

    /** Invokes the backing-storage transfer callback with the current viewer context. */
    public void transfer(InventoryContext context) {
        transfer.apply(context);
    }

    /** Returns completion of the backing transfer; menu render stacks never become transferable items. */
    public CompletableFuture<?> transferAsync(InventoryContext context) {
        return Objects.requireNonNull(transfer.apply(context), "transfer completion");
    }
}
