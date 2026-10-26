package dev.vexsoft.core.common.data.sql;

import dev.vexsoft.core.common.data.profile.JdbcProfileDocuments.Connections;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/** Shares an existing JDBC pool and executor without owning credentials or another connection pool. */
public final class JdbcSharedDatabase implements SharedDatabase {

    private final Connections connections;
    private final Executor executor;
    private final Semaphore pending = new Semaphore(256);

    public JdbcSharedDatabase(final Connections connections, final Executor executor) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public <T> CompletableFuture<T> transaction(final Work<T> work) {
        Objects.requireNonNull(work, "work");
        if (!pending.tryAcquire()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Shared database queue is full"));
        }
        var result = new CompletableFuture<T>();
        try {
            // Own the runnable separately: cancelling an AsyncSupply before execution skips its finally block.
            executor.execute(() -> {
                try {
                    if (!result.isDone()) {
                        result.complete(execute(work));
                    }
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                } finally {
                    pending.release();
                }
            });
            return result;
        } catch (RuntimeException failure) {
            pending.release();
            return CompletableFuture.failedFuture(failure);
        }
    }

    private <T> T execute(final Work<T> work) {
        try (var connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Shared database transaction failed", failure);
        }
    }
}
