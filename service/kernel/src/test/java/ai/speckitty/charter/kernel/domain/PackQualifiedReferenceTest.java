package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The reference grammar: a bare reference, a pack-qualified one whose pack name is a pack id (#582), an opaque URN,
 * and the qualified form's refusals.
 */
class PackQualifiedReferenceTest {

    @Test
    void aQualifiedReferenceNamesItsPackAndItsUrn() {
        PackQualifiedReference reference = PackQualifiedReference.parse("writing::directive:DIRECTIVE_009");

        assertThat(reference.qualified()).isTrue();
        assertThat(reference.packName()).isEqualTo(Optional.of("writing"));
        assertThat(reference.urn()).isEqualTo("directive:DIRECTIVE_009");
        assertThat(reference.toString()).isEqualTo("writing::directive:DIRECTIVE_009");
        assertThat(reference).isEqualTo(new PackQualifiedReference(Optional.of("writing"), "directive:DIRECTIVE_009"));
    }

    /** An entry of a list of one kind reads with its kind written out, or not at all (#673). */
    @ParameterizedTest
    @CsvSource(
            nullValues = "-",
            value = {
                "DIRECTIVE_001, directive:DIRECTIVE_001",
                "directive:DIRECTIVE_001, directive:DIRECTIVE_001",
                "base::DIRECTIVE_001, base::directive:DIRECTIVE_001",
                "base::directive:DIRECTIVE_001, base::directive:DIRECTIVE_001",
                "tactic:not-a-directive, -",
                "a::b::DIRECTIVE_001, -",
                "::DIRECTIVE_001, -"
            })
    void anEntryOfADirectiveListReadsAsADirectiveReference(String written, String read) {
        assertThat(PackQualifiedReference.ofKind(NodeKind.DIRECTIVE, written).map(Object::toString))
                .isEqualTo(Optional.ofNullable(read));
    }

    /** A pack name is a pack id (#582): a reference to a pack no pack id can name is refused when it is parsed. */
    @Test
    void aReferenceWhosePackNameHoldsAnUnderscoreIsRefusedAtParse() {
        assertThatThrownBy(() -> PackQualifiedReference.parse("a_b::directive:D1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(PackIdentifier.GRAMMAR_DETAIL)
                .hasMessageContaining("a_b");
        assertThat(PackQualifiedReference.parse("a-b::directive:D1").packName()).contains("a-b");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "a--b::directive:D1",
                "a-::directive:D1",
                "-a::directive:D1",
                "open_packs-2::directive:DIRECTIVE_009",
                "A::directive:D1"
            })
    void aPackNameOffThePackIdGrammarIsRefusedAtParse(String text) {
        assertThatThrownBy(() -> PackQualifiedReference.parse(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(PackIdentifier.GRAMMAR_DETAIL);
    }

    @Test
    void aPackNameIsCappedAtTheLongestPackId() {
        String atCap = "p".repeat(PackIdentifier.MAX_LENGTH);

        assertThat(PackQualifiedReference.parse(atCap + "::directive:D1").packName())
                .contains(atCap);
        assertThat(PackQualifiedReference.parse("1a::directive:D1").packName()).contains("1a");
        assertThatThrownBy(() -> PackQualifiedReference.parse(atCap + "p::directive:D1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(PackIdentifier.GRAMMAR_DETAIL);
    }

    @ParameterizedTest
    @CsvSource({"a_b,false", "a--b,false", "a-,false", "a-b,true", "1a,true", "open-packs-2,true"})
    void aPackNameIsAPackId(String name, boolean packName) {
        assertThat(PackQualifiedReference.isPackName(name)).isEqualTo(packName);
        assertThat(PackQualifiedReference.isPackName(name)).isEqualTo(PackIdentifier.isPackId(name));
    }

    @Test
    void aBareReferenceIsABareReferenceNotAnError() {
        PackQualifiedReference reference = PackQualifiedReference.parse("directive:DIRECTIVE_009");

        assertThat(reference.qualified()).isFalse();
        assertThat(reference.packName()).isEmpty();
        assertThat(reference.urn()).isEqualTo("directive:DIRECTIVE_009");
        assertThat(reference.toString()).isEqualTo("directive:DIRECTIVE_009");
    }

    @Test
    void aUrnHoldingColonsAndDotsSurvivesIntact() {
        PackQualifiedReference qualified = PackQualifiedReference.parse("foundation::principle:keep.it:whole");
        PackQualifiedReference bare = PackQualifiedReference.parse("principle:keep.it:whole");

        assertThat(qualified.urn()).isEqualTo("principle:keep.it:whole");
        assertThat(qualified.toString()).isEqualTo("foundation::principle:keep.it:whole");
        assertThat(bare.urn()).isEqualTo("principle:keep.it:whole");
    }

    @Test
    void aQualifiedReferenceWithoutAPackNameIsRefused() {
        assertThatThrownBy(() -> PackQualifiedReference.parse("::directive:DIRECTIVE_009"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names a pack before ::");
    }

    @Test
    void aQualifiedReferenceWithoutAUrnIsRefused() {
        assertThatThrownBy(() -> PackQualifiedReference.parse("writing::"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty URN");
    }

    @Test
    void aReferenceWithASecondQualifierIsRefused() {
        assertThatThrownBy(() -> PackQualifiedReference.parse("writing::directive:DIRECTIVE_009::extra"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most one :: qualifier");
    }

    @Test
    void aPackNameOutsideTheGrammarIsRefused() {
        assertThatThrownBy(() -> PackQualifiedReference.parse("Writing::directive:DIRECTIVE_009"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(PackIdentifier.GRAMMAR_DETAIL);
        assertThat(PackQualifiedReference.isPackName("Writing")).isFalse();
    }
}
