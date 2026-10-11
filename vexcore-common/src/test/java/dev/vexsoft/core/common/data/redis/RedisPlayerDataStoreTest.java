package dev.vexsoft.core.common.data.redis;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.messaging.MessageKey;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.player.profile.PlayerDataAddress;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.common.data.SqlitePlayerDataStore;
import io.lettuce.core.RedisClient;
import io.lettuce.core.AclSetuserArgs;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.protocol.CommandType;
import dev.vexsoft.core.common.service.messaging.RedisMessageChannel;
import dev.vexsoft.core.common.service.messaging.MessageEnvelope;
import dev.vexsoft.core.common.service.messaging.VexMessageCodecService;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Exercises the real Redis scripts through independent backend connections; requires VEXCORE_TEST_REDIS. */
class RedisPlayerDataStoreTest {

    @TempDir
    Path directory;
    private final List<RedisPlayerDataStore> stores = new ArrayList<>();
    private RedisClient inspector;
    private StatefulRedisConnection<String, String> inspection;
    private RedisSettings settings;
    private RedisPlayerDataStore first;
    private String root;

    @BeforeEach
    void start() {
        String endpoint = System.getenv("VEXCORE_TEST_REDIS");
        assumeTrue(endpoint != null && !endpoint.isBlank(), "Set VEXCORE_TEST_REDIS for Redis integration tests");
        settings = new RedisSettings(RedisURI.create(endpoint), "vexcore:test:" + UUID.randomUUID() + ':',
            "test", 128, 1000, 300, Duration.ofSeconds(3));
        root = settings.prefix() + "{network}:";
        inspector = RedisClient.create(settings.uri());
        inspection = inspector.connect();
        first = backend();
    }

    @AfterEach
    void stop() {
        stores.forEach(RedisPlayerDataStore::close);
        if (inspection != null) {
            var keys = inspection.sync().keys(settings.prefix() + '*');
            if (!keys.isEmpty()) {
                inspection.sync().del(keys.toArray(String[]::new));
            }
            inspection.close();
            inspector.shutdown();
        }
    }

    @Test
    void competingPickupsHaveExactlyOneWinner() {
        var second = backend();
        var item = shared("drop");
        assertTrue(first.commitProfileDocuments(List.of(new ProfileDocumentChange(item, 0, "{\"amount\":1}"))).join());
        var left = first.commitProfileDocuments(List.of(new ProfileDocumentChange(item, 1, "{\"amount\":0}")));
        var right = second.commitProfileDocuments(List.of(new ProfileDocumentChange(item, 1, "{\"amount\":0}")));
        assertEquals(1, (left.join() ? 1 : 0) + (right.join() ? 1 : 0));
        assertEquals(2, first.loadProfileDocuments(List.of(item)).join().get(item).revision());
    }

    @Test
    void conflictCannotPartiallyWriteAccountOrSharedBank() {
        UUID player = UUID.randomUUID();
        var key = DataContainerKey.of("coins", String.class, () -> "0");
        first.load("test", player, List.of(key)).join();
        var bank = shared("bank");
        first.commitProfileDocuments(List.of(new ProfileDocumentChange(bank, 0, "\"100\""))).join();
        assertFalse(first.commitPlayer(player, Map.of("test", Map.of("coins", "\"10\"")),
            List.of(new ProfileDocumentChange(bank, 0, "\"90\""))).join());
        assertTrue(first.load("test", player, List.of(key)).join().isEmpty());
        assertEquals("\"100\"", first.loadProfileDocuments(List.of(bank)).join().get(bank).value());
        assertTrue(first.commitPlayer(player, Map.of("test", Map.of("coins", "\"10\"")),
            List.of(new ProfileDocumentChange(bank, 1, "\"90\""))).join());
        assertEquals("\"10\"", first.load("test", player, List.of(key)).join().get("coins"));
    }

    @Test
    void expiredLeaseRejectsEveryWrite() {
        var bank = shared("bank");
        assertFalse(first.commitProfileDocuments(List.of(
            new ProfileDocumentChange(bank, 0, "{}", first.profileDatabaseTime().join() - 1))).join());
        assertTrue(first.loadProfileDocuments(List.of(bank)).join().isEmpty());
    }

    @Test
    void retryReceiptDoesNotApplyOneOperationTwice() throws Exception {
        String script;
        try (var resource = getClass().getResourceAsStream("/redis/commit.lua")) {
            script = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        String[] keys = {root + "state", root + "epoch", root + "journal", root + "receipts", root + "receipt-ages"};
        String[] arguments = {inspection.sync().get(root + "epoch"), "stable-operation",
            "[{\"key\":\"g|test|value\",\"expected\":0,\"value\":\"{}\",\"deadline\":0,\"remove\":false}]",
            "1000", Long.toString(first.profileDatabaseTime().join())};
        String once = inspection.sync().eval(script, ScriptOutputType.VALUE, keys, arguments);
        String twice = inspection.sync().eval(script, ScriptOutputType.VALUE, keys, arguments);
        assertEquals(once, twice);
        assertEquals(1, inspection.sync().xlen(root + "journal"));
        assertEquals(1, first.loadGlobalData("test", "value").join().orElseThrow().revision());
    }

    @Test
    void recoveryUsesTheWholeCompletedCheckpoint() {
        var inventory = shared("inventory");
        var money = shared("money");
        first.commitProfileDocuments(List.of(new ProfileDocumentChange(inventory, 0, "\"item\""),
            new ProfileDocumentChange(money, 0, "\"0\""))).join();
        first.checkpoint().join();
        first.commitProfileDocuments(List.of(new ProfileDocumentChange(inventory, 1, "\"empty\""),
            new ProfileDocumentChange(money, 1, "\"100\""))).join();
        // Simulate loss of the Redis authority, not just loss of one Paper process.
        inspection.sync().del(root + "epoch");
        var recovered = backend();
        var state = recovered.loadProfileDocuments(List.of(inventory, money)).join();
        assertEquals("\"item\"", state.get(inventory).value());
        assertEquals("\"0\"", state.get(money).value());
        assertThrows(Exception.class, () -> first.loadProfileDocuments(List.of(inventory)).join());
        recovered.commitProfileDocuments(List.of(new ProfileDocumentChange(money, 1, "\"10\""))).join();
        recovered.checkpoint().join();
    }

    @Test
    void checkpointsCoalesceWritesAndRemovalsInJournalOrder() {
        first.setGlobalData("test", "recreated", "1").join();
        first.setGlobalData("test", "deleted", "1").join();
        first.setGlobalData("test", "untouched", "1").join();
        first.checkpoint().join();
        assertTrue(first.resetGlobalData("test", "recreated").join());
        first.setGlobalData("test", "recreated", "2").join();
        first.setGlobalData("test", "deleted", "2").join();
        assertTrue(first.resetGlobalData("test", "deleted").join());
        first.checkpoint().join();
        inspection.sync().del(root + "epoch");
        var recovered = backend();
        assertEquals("2", recovered.loadGlobalData("test", "recreated").join().orElseThrow().value());
        assertTrue(recovered.loadGlobalData("test", "deleted").join().isEmpty());
        assertEquals("1", recovered.loadGlobalData("test", "untouched").join().orElseThrow().value());
    }

    @Test
    void liveStateSurvivesRestartWithoutAnIndividualSqlSave() {
        var value = shared("progress");
        first.commitProfileDocuments(List.of(new ProfileDocumentChange(value, 0, "\"new\""))).join();
        var second = backend();
        assertEquals("\"new\"", second.loadProfileDocuments(List.of(value)).join().get(value).value());
        var checkpoint = new SqlitePlayerDataStore(directory.resolve("checkpoint.db"));
        try {
            assertTrue(checkpoint.networkCheckpoints().load(settings.prefix()).join().values().isEmpty());
        } finally {
            checkpoint.close();
        }
    }

    @Test
    void missingStateFailsClosedInsteadOfReloadingStaleSql() {
        inspection.sync().del(root + "state");
        assertThrows(Exception.class, () -> first.loadGlobalData("test", "value").join());
        assertThrows(Exception.class, () -> first.setGlobalData("test", "value", "{}").join());
        assertEquals(0, inspection.sync().xlen(root + "journal"));
    }

    @Test
    void aLostJournalRejectsWritesAndCannotMixANewCheckpoint() {
        first.setGlobalData("test", "money", "\"100\"").join();
        first.checkpoint().join();
        inspection.sync().del(root + "journal");
        assertThrows(Exception.class, () -> first.setGlobalData("test", "money", "\"200\"").join());
        assertThrows(Exception.class, () -> first.checkpoint().join());
        assertThrows(Exception.class, this::backend);
        assertEquals("\"100\"", first.loadGlobalData("test", "money").join().orElseThrow().value());
    }

    @Test
    void wrongReceiptTypeCannotPartiallyWriteState() {
        inspection.sync().set(root + "receipt-ages", "wrong-type");
        assertThrows(Exception.class, () -> first.setGlobalData("test", "value", "{}").join());
        assertTrue(first.loadGlobalData("test", "value").join().isEmpty());
        assertEquals(0, inspection.sync().xlen(root + "journal"));
    }

    @Test
    void restrictedWritePermissionsCannotJournalAnUncommittedMutation() {
        String username = "vexcore-test-" + UUID.randomUUID();
        inspection.sync().aclSetuser(username, new AclSetuserArgs().on().addPassword("test-password")
            .keyPattern(settings.prefix() + '*').allCommands());
        try {
            RedisURI uri = RedisURI.Builder.redis(settings.uri().getHost(), settings.uri().getPort())
                .withDatabase(settings.uri().getDatabase()).withSsl(settings.uri().isSsl())
                .withAuthentication(username, "test-password").withTimeout(Duration.ofSeconds(2)).build();
            var restricted = new RedisPlayerDataStore(new SqlitePlayerDataStore(directory.resolve("checkpoint.db")),
                new RedisSettings(uri, settings.prefix(), "restricted-test", 128, 1000, 300, Duration.ofSeconds(3)));
            stores.add(restricted);
            restricted.readiness().join();
            inspection.sync().aclSetuser(username, new AclSetuserArgs().removeCommand(CommandType.HSET));
            assertThrows(Exception.class, () -> restricted.setGlobalData("test", "value", "{}").join());
            assertEquals(0, inspection.sync().xlen(root + "journal"));
            assertTrue(first.loadGlobalData("test", "value").join().isEmpty());
        } finally {
            inspection.sync().aclDeluser(username);
        }
    }

    @Test
    void aStaleAccountCopyCannotOverwriteTheNewerCopy() {
        UUID player = UUID.randomUUID();
        var key = DataContainerKey.of("language", String.class, () -> "de_DE");
        var second = backend();
        first.load("test", player, List.of(key)).join();
        second.load("test", player, List.of(key)).join();
        first.save("test", player, "Test", Map.of("language", "\"de_DE\"")).join();
        assertEquals("\"de_DE\"", second.readStored("test", player, List.of(key)).join().get("language"));
        assertThrows(Exception.class, () -> second.save("test", player, "Test", Map.of("language", "\"en_US\"")).join());
        assertEquals("\"de_DE\"", first.load("test", player, List.of(key)).join().get("language"));
    }

    @Test
    void anAccountResetCanBeFollowedByAConfirmedDefaultSave() {
        UUID player = UUID.randomUUID();
        var key = DataContainerKey.of("language", String.class, () -> "de_DE");
        first.load("test", player, List.of(key)).join();
        first.save("test", player, "Test", Map.of("language", "\"en_US\"")).join();
        first.reset("test", player, List.of("language")).join();
        first.save("test", player, "Test", Map.of("language", "\"de_DE\"")).join();
        assertEquals("\"de_DE\"", first.readStored("test", player, List.of(key)).join().get("language"));
        first.save("test", player, "Test", Map.of("language", "\"fr_FR\"")).join();
        assertEquals("\"fr_FR\"", first.readStored("test", player, List.of(key)).join().get("language"));
    }

    @Test
    void messagesReachVelocityAndAnEmptyBackendWithoutAPlayerCarrier() throws Exception {
        var codec = new VexMessageCodecService(new DefaultServiceRegistry().scoped(() -> "test"));
        try (var sender = new RedisMessageChannel(first, codec, false, "a", ignored -> false);
             var receiver = new RedisMessageChannel(backend(), codec, false, "b", ignored -> false);
             var proxy = new RedisMessageChannel(backend(), codec, true, "", ignored -> false)) {
            var toProxy = new CompletableFuture<MessageEnvelope>();
            var toServer = new CompletableFuture<MessageEnvelope>();
            proxy.subscribe(toProxy::complete);
            receiver.subscribe(toServer::complete);
            sender.start();
            receiver.start();
            proxy.start();
            CompletableFuture.allOf(sender.readiness(), receiver.readiness(), proxy.readiness()).join();
            sender.send(message(MessageTarget.proxy()));
            assertEquals("a", toProxy.get(3, TimeUnit.SECONDS).getSourceServer());
            proxy.send(message(MessageTarget.server("b")));
            assertEquals("", toServer.get(3, TimeUnit.SECONDS).getSourceServer());
        }
    }

    @Test
    void duplicateBackendEndpointFencesTheConflictingInstance() {
        var second = backend();
        var codec = new VexMessageCodecService(new DefaultServiceRegistry().scoped(() -> "test"));
        try (var owner = new RedisMessageChannel(first, codec, false, "a", ignored -> false);
             var duplicate = new RedisMessageChannel(second, codec, false, "a", ignored -> false)) {
            owner.start();
            owner.readiness().join();
            duplicate.start();
            assertThrows(Exception.class, () -> duplicate.readiness().join());
            assertThrows(Exception.class, () -> second.readiness().join());
            first.readiness().join();
        }
    }

    private MessageEnvelope message(final MessageTarget target) {
        return new MessageEnvelope(UUID.randomUUID(), MessageKey.of("test", "request"), target, "test", "forged",
            System.currentTimeMillis(), 30000, new byte[]{1});
    }

    private RedisPlayerDataStore backend() {
        var backend = new RedisPlayerDataStore(new SqlitePlayerDataStore(directory.resolve("checkpoint.db")), settings);
        stores.add(backend);
        backend.readiness().join();
        return backend;
    }

    private ProfileDocumentKey shared(final String name) {
        return new ProfileDocumentKey(PlayerDataAddress.shared(new ProfileDomain("test:profile"), new UUID(0, 1)),
            "test", name);
    }
}
