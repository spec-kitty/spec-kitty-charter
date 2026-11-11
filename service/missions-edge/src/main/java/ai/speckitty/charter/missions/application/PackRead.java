package ai.speckitty.charter.missions.application;

import java.util.Objects;

/**
 * A pack-scoped read's answer (also what the {@link PackMissionTypes} port answers), the source fingerprint of the inputs it was read from and the pack revision it was read
 * at, all from one verified read, so a caller never reports a fingerprint or a tag it took separately.
 *
 * @param value what the read answered
 * @param fingerprint the digest the read verified, {@code sha256:} and 64 lower-case hex digits
 * @param revision the pack revision, the read's {@code ETag}
 * @param <T> the type of the answer
 */
public record PackRead<T>(T value, String fingerprint, String revision) {

    /**
     * Checks the fields.
     *
     * @param value the answer
     * @param fingerprint the verified digest
     * @param revision the pack revision
     */
    public PackRead {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(revision, "revision");
    }

    /** A fingerprinted answer read at a revision. */
    static <T> PackRead<T> of(Fingerprinted<T> read, String revision) {
        return new PackRead<>(read.value(), read.fingerprint(), revision);
    }
}
