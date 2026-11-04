package ai.speckitty.charter.kernel.domain;

import java.util.Objects;

/**
 * One pack dependency a {@code pack.yaml} declares: the depended-on pack's {@code pack_id} and a PEP 440 version range
 * the held version must satisfy (ADR-0048 decision 1, ADR-0053 decision 4). The schema is closed, so the record holds
 * only what the file states; {@code PackPolicy} decides what a range that does not parse means, and the resolver
 * decides whether the held version satisfies it.
 *
 * @param packId the depended-on pack's {@code pack_id}, a ULID
 * @param range the raw PEP 440 specifier set, for example one that admits a 1.x version and excludes 2.0
 */
public record PackDependency(String packId, String range) {

    /**
     * A declared dependency.
     *
     * @param packId the depended-on pack id
     * @param range the version range
     */
    public PackDependency {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(range, "range");
        if (packId.isBlank()) {
            throw new IllegalArgumentException("packId must not be blank");
        }
        if (range.isBlank()) {
            throw new IllegalArgumentException("range must not be blank");
        }
    }
}
