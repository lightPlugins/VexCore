package dev.vexsoft.core.paper.service.block;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.block.ResourceBlock;
import dev.vexsoft.core.paper.block.ResourceBlockProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import java.util.Set;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/** Namespace resolver with an exact Vanilla BlockData adapter. */
@Dependencies({})
public final class VexResourceBlockService implements ResourceBlockService {

    private final Map<String, ResourceBlockProvider> providers = new ConcurrentHashMap<>();
    private final ResourceBlockProvider vanilla = new VanillaProvider();

    public VexResourceBlockService(final VexServiceRegistry services) {
        Objects.requireNonNull(services, "services");
        providers.put("minecraft", vanilla);
    }

    @Override
    public AutoCloseable register(final String namespace, final ResourceBlockProvider provider) {
        if (!namespace.matches("[a-z0-9._-]+")
            || providers.putIfAbsent(namespace, Objects.requireNonNull(provider, "provider")) != null) {
            throw new IllegalArgumentException("Invalid or duplicate block provider: " + namespace);
        }
        return () -> providers.remove(namespace, provider);
    }

    @Override
    public ResourceBlock capture(final Block block) {
        for (var provider : providers.values()) {
            if (provider != vanilla && provider.recognizes(block)) {
                return provider.capture(block);
            }
        }
        return vanilla.capture(block);
    }

    @Override
    public void validate(final ResourceBlock definition) {
        provider(definition).validate(definition);
    }

    @Override
    public Set<NamespacedKey> adventureMaterials(final ResourceBlock definition) {
        return Set.copyOf(provider(definition).adventureMaterials(definition));
    }

    @Override
    public boolean isAttachedTo(final Block block, final Block support) {
        return provider(capture(block)).isAttachedTo(block, support);
    }

    @Override
    public void place(final Block block, final ResourceBlock definition) {
        provider(definition).place(block, definition);
    }

    private ResourceBlockProvider provider(final ResourceBlock block) {
        var provider = providers.get(block.key().substring(0, block.key().indexOf(':')));
        if (provider == null) {
            throw new IllegalArgumentException("Missing block provider for " + block.key());
        }
        return provider;
    }

    private static final class VanillaProvider implements ResourceBlockProvider {

        @Override
        public Set<NamespacedKey> adventureMaterials(final ResourceBlock definition) {
            return Set.of(Objects.requireNonNull(NamespacedKey.fromString(definition.key())));
        }

        @Override
        public boolean recognizes(final Block block) {
            return true;
        }

        @Override
        public ResourceBlock capture(final Block block) {
            String data = block.getBlockData().getAsString();
            int open = data.indexOf('[');
            Map<String, String> properties = new LinkedHashMap<>();
            if (open >= 0) {
                for (String property : data.substring(open + 1, data.length() - 1).split(",")) {
                    String[] pair = property.split("=", 2);
                    properties.put(pair[0], pair[1]);
                }
            }
            return new ResourceBlock(open < 0 ? data : data.substring(0, open), properties);
        }

        @Override
        public void validate(final ResourceBlock definition) {
            Bukkit.createBlockData(data(definition));
        }

        @Override
        public boolean isAttachedTo(final Block block, final Block support) {
            String key = block.getType().getKey().asString();
            return block.getRelative(BlockFace.DOWN).equals(support)
                && (key.equals("minecraft:cactus_flower") || key.equals("minecraft:bamboo_sapling"));
        }

        @Override
        public void place(final Block block, final ResourceBlock definition) {
            block.setBlockData(Bukkit.createBlockData(data(definition)), false);
        }

        private static String data(final ResourceBlock definition) {
            return definition.key() + (definition.properties().isEmpty() ? "" : "["
                + definition.properties().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(",")) + "]");
        }
    }
}
