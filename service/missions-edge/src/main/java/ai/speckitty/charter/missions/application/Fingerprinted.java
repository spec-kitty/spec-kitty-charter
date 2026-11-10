package ai.speckitty.charter.missions.application;

import java.util.Objects;

/**
 * A read's answer and the source fingerprint of the inputs it was read from, so a caller never reports a fingerprint it
 * took separately.
 *
 * @param value what the read answered
 * @param fingerprint the digest the read verified, {@code sha256:} and 64 lower-case hex digits
 * @param <T> the type of the answer
 */
public record Fingerprinted<T>(T value, String fingerprint) {

    /**
     * Checks the fields.
     *
     * @param value the answer
     * @param fingerprint the verified digest
     */
    public Fingerprinted {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
