package ai.speckitty.charter.kernel.domain;

import java.util.Objects;

/**
 * One sibling contest a distribution pack's {@code pack.yaml} settles: the contested artifact and the member pack
 * whose version of it wins (ADR-0048 decision 9, its open question 5 as ruled by ADR-0053 decision 5). Authored on
 * the distribution pack, never derived. The schema is closed, so the record holds only what the file states; whether
 * the member is part of the compose is the distribution-pack flow's business, not this value's.
 *
 * @param artifact the contested artifact's reference, opaque here (C-005: no URN parsing or re-spelling)
 * @param member the {@code pack_id} of the pack whose version wins
 */
public record PackOverride(String artifact, String member) {

    /**
     * A settled contest.
     *
     * @param artifact the contested artifact
     * @param member the winning member pack id
     */
    public PackOverride {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(member, "member");
        if (artifact.isBlank()) {
            throw new IllegalArgumentException("artifact must not be blank");
        }
        if (member.isBlank()) {
            throw new IllegalArgumentException("member must not be blank");
        }
    }
}
