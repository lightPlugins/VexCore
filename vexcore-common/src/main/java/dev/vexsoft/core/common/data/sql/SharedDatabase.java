package dev.vexsoft.core.common.data.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

/** Runs feature-owned SQL transactions on Core's durable pool, including when Redis fronts player data. */
public interface SharedDatabase {

    /** Executes and commits one transaction off the server thread; failures roll back all changes. */
    <T> CompletableFuture<T> transaction(Work<T> work);

    /** A bounded database operation; connections and result sets must not escape the callback. */
    @FunctionalInterface
    interface Work<T> {

        /** Reads or changes feature-owned tables within the supplied transaction. */
        T execute(Connection connection) throws SQLException;
    }
}
