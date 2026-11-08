package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.TACTIC;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ArtifactSpellings.Resolution;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The known spellings of an artifact id (ADR-0020 D4, operator ruling of 2026-11-06). */
class ArtifactSpellingsTest {

    private static final Set<String> HELD = Set.of("DIRECTIVE_001", "Mixed", "Twin", "TWIN");

    @Test
    void theDeclaredIdIsItsOwnSpelling() {
        assertThat(ArtifactSpellings.resolve(DIRECTIVE, "DIRECTIVE_001", HELD))
                .isEqualTo(new Resolution.Declared("DIRECTIVE_001"));
    }

    @Test
    void theFullUrnOfTheListsKindIsASpelling() {
        assertThat(ArtifactSpellings.resolve(DIRECTIVE, "directive:DIRECTIVE_001", HELD))
                .isEqualTo(new Resolution.Declared("DIRECTIVE_001"));
    }

    @Test
    void aCaseVariantIsASpelling() {
        assertThat(ArtifactSpellings.resolve(DIRECTIVE, "directive_001", HELD))
                .isEqualTo(new Resolution.Declared("DIRECTIVE_001"));
        assertThat(ArtifactSpellings.resolve(DIRECTIVE, "directive:MIXED", HELD))
                .isEqualTo(new Resolution.Declared("Mixed"));
    }

    @Test
    void theKindPrefixOfAUrnIsMatchedWithoutCase() {
        assertThat(ArtifactSpellings.resolve(DIRECTIVE, "DIRECTIVE:DIRECTIVE_001", HELD))
                .isEqualTo(new Resolution.Declared("DIRECTIVE_001"));
        assertThat(ArtifactSpellings.resolve(DIRECTIVE, "Directive:directive_001", HELD))
                .isEqualTo(new Resolution.Declared("DIRECTIVE_001"));
        assertThat(ArtifactSpellings.resolve(DIRECTIVE, "TACTIC:DIRECTIVE_001", HELD))
                .isEqualTo(new Resolution.Unknown());
    }

    @Test
    void aMatchBetweenTwoIdsThatDifferOnlyByCaseIsAmbiguousButTheExactIdIsNot() {
        assertThat(ArtifactSpellings.resolve(DIRECTIVE, "twin", HELD))
                .isEqualTo(new Resolution.Ambiguous(java.util.List.of("TWIN", "Twin")));
        assertThat(ArtifactSpellings.resolve(DIRECTIVE, "Twin", HELD)).isEqualTo(new Resolution.Declared("Twin"));
        assertThat(ArtifactSpellings.resolve(DIRECTIVE, "directive:TWIN", HELD))
                .isEqualTo(new Resolution.Declared("TWIN"));
    }

    @Test
    void aUrnOfAnotherKindAndAnIdNoneHoldsAreUnknown() {
        assertThat(ArtifactSpellings.resolve(DIRECTIVE, "tactic:DIRECTIVE_001", HELD))
                .isEqualTo(new Resolution.Unknown());
        assertThat(ArtifactSpellings.resolve(TACTIC, "directive:DIRECTIVE_001", Set.of("DIRECTIVE_001")))
                .isEqualTo(new Resolution.Unknown());
        assertThat(ArtifactSpellings.resolve(DIRECTIVE, "NOPE", HELD)).isEqualTo(new Resolution.Unknown());
        assertThat(ArtifactSpellings.resolve(DIRECTIVE, "directive:", HELD)).isEqualTo(new Resolution.Unknown());
    }
}
