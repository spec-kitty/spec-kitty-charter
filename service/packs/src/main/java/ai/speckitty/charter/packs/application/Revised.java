package ai.speckitty.charter.packs.application;

import java.util.Objects;

/**
 * A value read together with the pack revision it was read at, both taken inside one {@link ConsistentRead}, so the
 * {@code ETag} an item read answers with names the very state its body came from.
 *
 * @param value what was read
 * @param revision the pack's revision at the time of the read, for the {@code ETag}
 * @param <T> the type of the value
 */
public record Revised<T>(T value, PackRevision revision) {

    /**
     * A value and its revision.
     *
     * @param value what was read
     * @param revision the pack's revision at the time of the read
     */
    public Revised {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(revision, "revision");
    }
}
