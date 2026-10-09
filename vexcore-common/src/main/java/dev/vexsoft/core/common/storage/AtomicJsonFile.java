package dev.vexsoft.core.common.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/** Local JSON snapshot writer that publishes only completely written and flushed files. */
public final class AtomicJsonFile<T> {

    private final Path path;
    private final Class<T> type;
    private final ObjectMapper mapper;

    /** Creates a file store; callers serialize reads and writes on their own background executor. */
    public AtomicJsonFile(final Path path, final Class<T> type, final ObjectMapper mapper) {
        this.path = path;
        this.type = type;
        this.mapper = mapper;
    }

    /** Reads the snapshot if present and fails rather than replacing malformed state with defaults. */
    public Optional<T> read() throws IOException {
        return Files.exists(path) ? Optional.of(mapper.readValue(path.toFile(), type)) : Optional.empty();
    }

    /** Flushes a replacement snapshot before atomically exchanging it with the previous file. */
    public void write(final T snapshot) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        byte[] bytes = mapper.writeValueAsBytes(snapshot);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
