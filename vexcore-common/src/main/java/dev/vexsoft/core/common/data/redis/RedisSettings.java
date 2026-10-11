package dev.vexsoft.core.common.data.redis;

import dev.vexsoft.core.api.configuration.VexConfiguration;
import io.lettuce.core.RedisURI;
import java.time.Duration;

/** Validated connection, namespace and bounded journal settings from database.yml. */
public record RedisSettings(
    RedisURI uri, String prefix, String clientName, int maximumPendingCommands,
    int maximumJournalEntries, int checkpointIntervalSeconds, Duration connectTimeout
) {

    public static RedisSettings read(final VexConfiguration configuration) {
        String prefix = configuration.getString("redis.key-prefix", "vexcore:production:");
        if (!prefix.matches("[a-zA-Z0-9:_-]{1,160}")) {
            throw new IllegalArgumentException("redis.key-prefix must be a non-empty safe namespace");
        }
        String host = configuration.getString("redis.host", "localhost");
        int port = bounded(configuration, "redis.port", 6379, 1, 65535);
        var uri = RedisURI.Builder.redis(host, port)
            .withDatabase(bounded(configuration, "redis.database", 0, 0, 15))
            .withSsl(configuration.getBoolean("redis.ssl", false))
            .withTimeout(Duration.ofMillis(bounded(configuration, "redis.command-timeout-millis", 1500, 100, 30000)));
        String password = configuration.getString("redis.password", "");
        if (!password.isEmpty()) {
            uri.withAuthentication(configuration.getString("redis.username", "default"), password);
        }
        String name = configuration.getString("redis.client-name", "vexcore");
        if (name.isBlank() || name.length() > 128) {
            throw new IllegalArgumentException("Invalid redis.client-name");
        }
        return new RedisSettings(uri.build(), prefix, name,
            bounded(configuration, "redis.maximum-pending-commands", 4096, 16, 65536),
            bounded(configuration, "redis.maximum-journal-entries", 1000000, 1000, 10000000),
            bounded(configuration, "persistence.checkpoint-interval-seconds", 300, 5, 3600),
            Duration.ofMillis(bounded(configuration, "redis.connect-timeout-millis", 3000, 100, 30000)));
    }

    private static int bounded(
        final VexConfiguration configuration, final String key, final int fallback, final int minimum, final int maximum
    ) {
        int value = configuration.getInt(key, fallback);
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(key + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }
}
