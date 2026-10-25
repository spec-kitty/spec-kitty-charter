package ai.speckitty.charter.packs.infra.storage;

import java.util.Objects;
import java.util.Optional;

/**
 * The storage-side serialization object of one DRG edge (ADR-0025 Decision 5): a flat mirror of a shard
 * edge's fields as they sit on the page, with {@code source}, {@code target} and {@code relation} as the
 * token strings. It is a persistence mirror, not a domain type; {@link RelationshipMapper} converts it to
 * and from the kernel {@code Relationship} at the YAML boundary, and the domain never sees it.
 *
 * <p>Record equality is the round-trip fidelity assertion (ADR-0024): two DSOs are equal exactly when every
 * field matches, present-vs-absent included. The DSO carries <strong>no</strong> cross-field rule (it is a
 * plain mirror): the {@code impacts}-iff-the-{@code impacts}-relation rule lives in {@code PackPolicy} on the
 * read side, and {@code Relationship}'s invariants (a parseable URN per endpoint, a known relation token, no
 * self-loop) live in {@link RelationshipMapper}. This keeps the DSO a faithful byte-for-byte mirror so a
 * malformed field surfaces as a mapper or policy fact rather than being refused at the persistence shape.
 *
 * @param source the edge's source as its URN token string
 * @param target the edge's target as its URN token string
 * @param relation the relation's token string, for example {@code requires}
 * @param impacts the signed {@code impacts} strength as written; empty for every other edge
 * @param when the edge's condition, as authored; empty when it has none
 * @param reason why the edge exists, as authored; empty when it has none
 */
record DrgEdge(
        String source,
        String target,
        String relation,
        Optional<Double> impacts,
        Optional<String> when,
        Optional<String> reason) {

    /**
     * Checks that every field is present, the optional ones possibly empty but never {@code null}.
     *
     * @param source the edge's source token
     * @param target the edge's target token
     * @param relation the relation token
     * @param impacts the signed {@code impacts} strength
     * @param when the edge's condition
     * @param reason why the edge exists
     */
    DrgEdge {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(impacts, "impacts");
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(reason, "reason");
    }
}
