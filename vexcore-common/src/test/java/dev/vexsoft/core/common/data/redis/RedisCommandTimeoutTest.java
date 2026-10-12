package dev.vexsoft.core.common.data.redis;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.protocol.ProtocolVersion;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Verifies bounded async commands against a connected peer that stops replying, without a Redis installation. */
class RedisCommandTimeoutTest {

    @Test
    void asyncCommandExpiresWhenTheConnectedPeerStopsReplying() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var received = new CompletableFuture<Void>();
            Thread peer = Thread.ofVirtual().start(() -> answerHandshake(server, received));
            var uri = RedisURI.Builder.redis(server.getInetAddress().getHostAddress(), server.getLocalPort())
                .withTimeout(Duration.ofMillis(300)).build();
            var settings = new RedisSettings(uri, "test:", "timeout-test", 16, 1000, 300, Duration.ofSeconds(2));
            var client = RedisClient.create(uri);
            client.setOptions(RedisPlayerDataStore.clientOptions(settings).mutate()
                .protocolVersion(ProtocolVersion.RESP2).build());
            try (var connection = client.connect()) {
                var command = connection.async().get("waiting").toCompletableFuture();
                received.get(3, TimeUnit.SECONDS);
                var failure = assertThrows(ExecutionException.class, () -> command.get(3, TimeUnit.SECONDS));
                assertInstanceOf(RedisCommandTimeoutException.class, failure.getCause());
            } finally {
                client.shutdown();
            }
            peer.join(Duration.ofSeconds(3));
        }
    }

    private static void answerHandshake(final ServerSocket server, final CompletableFuture<Void> received) {
        try (var socket = server.accept()) {
            var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            var output = socket.getOutputStream();
            String header;
            while ((header = input.readLine()) != null) {
                int arguments = Integer.parseInt(header.substring(1));
                var command = new ArrayList<String>();
                for (int index = 0; index < arguments; index++) {
                    int length = Integer.parseInt(input.readLine().substring(1));
                    var value = new char[length];
                    int offset = 0;
                    while (offset < length) {
                        int count = input.read(value, offset, length - offset);
                        if (count < 0) {
                            throw new IOException("Peer closed during a command");
                        }
                        offset += count;
                    }
                    input.readLine();
                    command.add(new String(value));
                }
                if (command.getFirst().equalsIgnoreCase("GET")) {
                    received.complete(null);
                    continue;
                }
                String reply = command.getFirst().equalsIgnoreCase("PING") ? "+PONG\r\n" : "+OK\r\n";
                output.write(reply.getBytes(StandardCharsets.US_ASCII));
                output.flush();
            }
        } catch (IOException | RuntimeException failure) {
            received.completeExceptionally(failure);
        }
    }
}
