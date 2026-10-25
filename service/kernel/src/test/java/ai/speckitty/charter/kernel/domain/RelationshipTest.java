package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RelationshipTest {

    private static final Urn A = new Urn(NodeKind.TACTIC, "a");
    private static final Urn B = new Urn(NodeKind.TACTIC, "b");
    private static final Urn C = new Urn(NodeKind.TACTIC, "c");

    @Test
    void identityIsTheUnorderedPairSoAForwardEqualsItsReverse() {
        Relationship forward = Relationship.of(A, B, RelationType.REQUIRES);
        Relationship reverse = Relationship.of(B, A, RelationType.REQUIRES);

        assertThat(forward).isEqualTo(reverse);
        assertThat(forward.hashCode()).isEqualTo(reverse.hashCode());
    }

    @Test
    void identityIgnoresTypeWhenAndRationaleSoAtMostOneRelationshipExistsPerPair() {
        Relationship requires = Relationship.of(A, B, RelationType.REQUIRES);
        Relationship suggests =
                new Relationship(B, A, RelationType.SUGGESTS, Optional.of("activated"), Optional.of("because"));

        assertThat(requires).isEqualTo(suggests);
        assertThat(requires.hashCode()).isEqualTo(suggests.hashCode());
    }

    @Test
    void aSetDeduplicatesTheReverseButKeepsAGenuinelyDifferentPair() {
        // Positive control: the reverse collapses, but a distinct pair survives — the assertion
        // above cannot pass vacuously (acceptance-criteria-non-vacuity, DIRECTIVE_041).
        Relationship forward = Relationship.of(A, B, RelationType.REQUIRES);
        Relationship reverse = Relationship.of(B, A, RelationType.REQUIRES);
        Relationship otherPair = Relationship.of(A, C, RelationType.REQUIRES);

        Set<Relationship> relationships = new HashSet<>();
        relationships.add(forward);
        relationships.add(reverse);
        relationships.add(otherPair);

        assertThat(relationships).hasSize(2).contains(forward, otherPair);
        assertThat(otherPair).isNotEqualTo(forward);
        assertThat(otherPair.hashCode()).isNotEqualTo(forward.hashCode());
    }

    @Test
    void aDirectedRelationshipStillReportsItsStoredOrientation() {
        Relationship forward = Relationship.of(A, B, RelationType.REQUIRES);

        assertThat(forward.from()).isEqualTo(A);
        assertThat(forward.to()).isEqualTo(B);
        assertThat(forward.type()).isEqualTo(RelationType.REQUIRES);
    }

    @Test
    void aSelfLoopIsRejected() {
        assertThatThrownBy(() -> Relationship.of(A, A, RelationType.REQUIRES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("self");
    }

    @Test
    void nullFieldsAreRejected() {
        assertThatThrownBy(() -> new Relationship(null, B, RelationType.REQUIRES, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Relationship(A, null, RelationType.REQUIRES, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Relationship(A, B, null, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullOptionalsAreRejected() {
        assertThatThrownBy(() -> new Relationship(A, B, RelationType.REQUIRES, null, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Relationship(A, B, RelationType.REQUIRES, Optional.empty(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whenIsRepresentableBothPresentAndEmptySoItCanRoundTrip() {
        Relationship withWhen =
                new Relationship(A, B, RelationType.REQUIRES, Optional.of("manifested"), Optional.empty());
        Relationship withoutWhen = Relationship.of(A, B, RelationType.REQUIRES);

        assertThat(withWhen.when()).contains("manifested");
        assertThat(withoutWhen.when()).isEmpty();
    }

    @Test
    void rationaleIsRepresentableBothPresentAndEmpty() {
        Relationship withRationale =
                new Relationship(A, B, RelationType.REQUIRES, Optional.empty(), Optional.of("the operator asked"));
        Relationship withoutRationale = Relationship.of(A, B, RelationType.REQUIRES);

        assertThat(withRationale.rationale()).contains("the operator asked");
        assertThat(withoutRationale.rationale()).isEmpty();
    }
}
