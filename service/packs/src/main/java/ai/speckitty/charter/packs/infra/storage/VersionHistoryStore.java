package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackVersionHistory.Entry;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The file of the version history (IC-16): {@code <writable root>/.history/<packId>.json}, a JSON list of
 * {@code {"pack_version", "revision", "registeredAt"}}. The directory starts with a dot, so the pack listing skips it,
 * and nothing that removes a pack touches it.
 *
 * <p>A write is two steps so that the commit store can do the first before its swap and the second right after it:
 * {@link #stage} writes the next history beside its file and forces it to disk, {@link #publish} renames it over the
 * file atomically. A file that does not read is {@code pack_unreadable}, never an empty history: reading it as empty
 * would let a label be reused.
 */
public final class VersionHistoryStore {

    private static final String DIRECTORY = ".history";
    private static final String PACK_VERSION = "pack_version";
    private static final String REVISION = "revision";
    private static final String REGISTERED_AT = "registeredAt";
    private static final Set<String> FIELDS = Set.of(PACK_VERSION, REVISION, REGISTERED_AT);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Path writableRoot;

    /**
     * A store under the directory packs are written into.
     *
     * @param writableRoot the writable pack root
     */
    public VersionHistoryStore(Path writableRoot) {
        this.writableRoot = Objects.requireNonNull(writableRoot, "writableRoot");
    }

    /**
     * The file that holds a pack name's history.
     *
     * @param packId the name the pack is stored under
     * @return {@code <root>/.history/<packId>.json}
     * @throws IllegalArgumentException when the name is no single path segment
     */
    public Path fileOf(String packId) {
        Objects.requireNonNull(packId, "packId");
        if (packId.isEmpty()
                || packId.equals(".")
                || packId.equals("..")
                || packId.contains("/")
                || packId.contains("\\")
                || packId.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("a pack name is one path segment, not '" + packId + "'");
        }
        return writableRoot.resolve(DIRECTORY).resolve(packId + ".json");
    }

    /**
     * Reads a pack name's history.
     *
     * @param packId the name the pack is stored under
     * @return the entries, oldest first; empty when the name was never registered
     * @throws CharterRefusal {@code PACK_UNREADABLE} when the file is there and is not a history
     */
    public List<Entry> read(String packId) {
        Path file = fileOf(packId);
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException never) {
            return List.of();
        } catch (IOException failure) {
            throw unreadable(packId, "cannot be read", failure);
        }
        try {
            return entriesOf(packId, JSON.readValue(text, Object.class));
        } catch (JacksonException notJson) {
            throw unreadable(packId, "is not JSON", notJson);
        }
    }

    private static List<Entry> entriesOf(String packId, Object document) {
        if (!(document instanceof List<?> list)) {
            throw unreadable(packId, "is not a list");
        }
        List<Entry> entries = new ArrayList<>();
        for (Object item : list) {
            entries.add(entryOf(packId, item));
        }
        return List.copyOf(entries);
    }

    private static Entry entryOf(String packId, Object item) {
        if (!(item instanceof Map<?, ?> fields) || !fields.keySet().equals(FIELDS)) {
            throw unreadable(packId, "holds an entry that is not {pack_version, revision, registeredAt}");
        }
        if (!(fields.get(PACK_VERSION) instanceof String version)
                || !(fields.get(REVISION) instanceof String revision)
                || !(fields.get(REGISTERED_AT) instanceof String at)) {
            throw unreadable(packId, "holds an entry whose fields are not all text");
        }
        try {
            return new Entry(version, revision, Instant.parse(at));
        } catch (DateTimeParseException notATime) {
            throw unreadable(packId, "holds an entry whose registeredAt is no instant", notATime);
        }
    }

    /**
     * Writes the next history beside its file, to disk, without publishing it.
     *
     * @param packId the name the pack is stored under
     * @param next the entries, oldest first
     * @return the staged file, to give to {@link #publish}
     * @throws CharterRefusal {@code PACK_UNREADABLE} when it cannot be written
     */
    public Path stage(String packId, List<Entry> next) {
        Path file = fileOf(packId);
        Path staged = file.resolveSibling(file.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.createDirectories(file.getParent());
            byte[] bytes = JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(
                            next.stream().map(VersionHistoryStore::fieldsOf).toList())
                    .concat("\n")
                    .getBytes(StandardCharsets.UTF_8);
            writeDurably(staged, bytes);
            return staged;
        } catch (IOException | JacksonException failure) {
            deleteQuietly(staged);
            throw unreadable(packId, "cannot be written", failure);
        }
    }

    private static Map<String, String> fieldsOf(Entry entry) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(PACK_VERSION, entry.packVersion());
        fields.put(REVISION, entry.revision());
        fields.put(REGISTERED_AT, entry.registeredAt().toString());
        return fields;
    }

    /**
     * Renames a staged history over the file, atomically.
     *
     * @param packId the name the pack is stored under
     * @param staged the file {@link #stage} returned
     * @throws CharterRefusal {@code PACK_UNREADABLE} when the rename fails; the staged file is removed
     */
    public void publish(String packId, Path staged) {
        Path file = fileOf(packId);
        try {
            try {
                Files.move(staged, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(staged, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            deleteQuietly(staged);
            throw unreadable(packId, "cannot be published", failure);
        }
    }

    /**
     * Removes a staged history that is not going to be published.
     *
     * @param staged the file {@link #stage} returned
     */
    public void discard(Path staged) {
        deleteQuietly(staged);
    }

    private static void writeDurably(Path file, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // the failure being reported is the one worth reading
        }
    }

    private static CharterRefusal unreadable(String packId, String why) {
        return new CharterRefusal(RefusalCode.PACK_UNREADABLE, detailOf(packId, why));
    }

    private static CharterRefusal unreadable(String packId, String why, Throwable cause) {
        return new CharterRefusal(RefusalCode.PACK_UNREADABLE, detailOf(packId, why), cause);
    }

    private static String detailOf(String packId, String why) {
        return "The version history of the pack " + packId + " " + why + "; it is kept in " + DIRECTORY + "/" + packId
                + ".json beside the packs.";
    }
}
