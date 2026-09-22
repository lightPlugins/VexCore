package dev.vexsoft.core.paper.service.screenui;

import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.cache.VexAsyncCache;
import dev.vexsoft.core.cache.VexCacheOptions;
import dev.vexsoft.core.paper.screenui.UiNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.bukkit.entity.Player;

/** Bounded asynchronous skin-face cache; no image downloads or decoding on player threads. */
public final class ScreenUiAvatarLoader implements AutoCloseable {

    private static final UiNode.Avatar FALLBACK = new UiNode.Avatar("avatar", Collections.nCopies(64, 0x64748B));
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final VexAsyncCache<String, UiNode.Avatar> cache;

    public ScreenUiAvatarLoader(CacheService caches) {
        cache = caches.createAsync(
            "screen-ui-avatars", VexCacheOptions.builder().maximumSize(256)
                .expireAfterWrite(Duration.ofMinutes(30)).build(), this::load
        );
    }

    public CompletableFuture<UiNode.Avatar> avatar(Player player) {
        var skin = player.getPlayerProfile().getTextures().getSkin();
        return skin == null ? CompletableFuture.completedFuture(FALLBACK)
            : cache.get(skin.toExternalForm()).thenApply(value -> value);
    }

    private CompletableFuture<UiNode.Avatar> load(String address) {
        URI source = URI.create(address);
        if (!"textures.minecraft.net".equalsIgnoreCase(source.getHost())
            || source.getRawUserInfo() != null || source.getPort() != -1
            || !source.getPath().matches("/texture/[a-fA-F0-9]+")) {
            return CompletableFuture.completedFuture(FALLBACK);
        }
        URI secure = URI.create("https://textures.minecraft.net" + source.getPath());
        HttpRequest request = HttpRequest.newBuilder(secure).timeout(Duration.ofSeconds(5)).GET().build();
        CompletableFuture<HttpResponse<byte[]>> response = client.sendAsync(request, info -> new LimitedBody());
        return response.copy().orTimeout(6, TimeUnit.SECONDS).thenApplyAsync(received -> {
            try {
                return received.statusCode() == 200 ? decode(received.body()) : FALLBACK;
            } catch (IOException exception) {
                return FALLBACK;
            }
        }).exceptionally(failure -> {
            response.cancel(true);
            return FALLBACK;
        });
    }

    static UiNode.Avatar decode(byte[] bytes) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return FALLBACK;
            }
            var reader = readers.next();
            try {
                reader.setInput(input);
                if (reader.getWidth(0) != 64 || (reader.getHeight(0) != 32 && reader.getHeight(0) != 64)) {
                    return FALLBACK;
                }
                BufferedImage image = reader.read(0);
                List<Integer> pixels = new ArrayList<>(64);
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        int face = image.getRGB(8 + x, 8 + y);
                        int hat = image.getRGB(40 + x, 8 + y);
                        int alpha = hat >>> 24;
                        int rgb = 0;
                        for (int shift : new int[]{16, 8, 0}) {
                            int value = (((hat >> shift) & 255) * alpha
                                + ((face >> shift) & 255) * (255 - alpha) + 127) / 255;
                            rgb |= value << shift;
                        }
                        pixels.add(rgb);
                    }
                }
                return new UiNode.Avatar("avatar", pixels);
            } finally {
                reader.dispose();
            }
        }
    }

    @Override
    public void close() {
        cache.invalidateAll();
        client.shutdownNow();
    }

    /** Cancels oversized bodies before allocating an unbounded byte array. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {

        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > 65536 - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("Skin exceeds 64 KiB"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable failure) {
            result.completeExceptionally(failure);
        }

        @Override
        public void onComplete() {
            result.complete(bytes.toByteArray());
        }
    }
}
