package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The reference grammar: a bare reference, a pack-qualified one, an opaque URN, and the qualified form's refusals. */
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

    @Test
    void aPackNameMayHoldDigitsUnderscoresAndHyphens() {
        assertThat(PackQualifiedReference.parse("open_packs-2::directive:DIRECTIVE_009")
                        .packName())
                .isEqualTo(Optional.of("open_packs-2"));
        assertThat(PackQualifiedReference.isPackName("open_packs-2")).isTrue();
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
                .hasMessageContaining("^[a-z0-9][a-z0-9_-]*$");
        assertThat(PackQualifiedReference.isPackName("Writing")).isFalse();
    }
}
