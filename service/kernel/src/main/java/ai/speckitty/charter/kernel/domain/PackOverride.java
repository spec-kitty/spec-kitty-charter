package ai.speckitty.charter.kernel.domain;

import java.util.Objects;

/**
 * One sibling conflict a pack's {@code pack.yaml} settles: the conflicting artifact and the member pack whose version
 * of it is meant to win (ADR-0048 decision 9, its open question 5 as ruled by ADR-0053 decision 5). Authored, never
 * derived. Until #358 any pack of the compose may declare it, not only the distribution pack; until #357 settling
 * only clears the refusal and the member's version is not yet put in force. The schema is closed, so the record holds
 * only what the file states; whether the member is part of the compose is the resolver's business, not this value's.
 *
 * @param artifact the conflicting artifact's reference, opaque here (C-005: no URN parsing or re-spelling)
 * @param member the {@code pack_id} of the pack whose version is meant to win
 */
public record PackOverride(String artifact, String member) {

    /**
     * A settled sibling conflict.
     *
     * @param artifact the conflicting artifact
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
