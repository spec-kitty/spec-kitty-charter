package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UrnTest {

    @Test
    void parsesKnownKinds() {
        assertThat(Urn.parse("action:software-dev/plan")).contains(new Urn(NodeKind.ACTION, "software-dev/plan"));
        assertThat(Urn.parse("directive:DIRECTIVE_010").map(Urn::toString)).contains("directive:DIRECTIVE_010");
    }

    @Test
    void aUrnIsBareAndAPackQualifiedReferenceIsNotOne() {
        assertThat(Urn.parse("writing-pack::directive:DIRECTIVE_010")).isEmpty();
        assertThatThrownBy(() -> new Urn(NodeKind.DIRECTIVE, "writing-pack::DIRECTIVE_010"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PackQualifiedReference.parse("writing-pack::directive:DIRECTIVE_010")
                        .asUrn())
                .contains(new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_010"));
    }

    @Test
    void refusesWhatIsNotAUrn() {
        assertThat(Urn.parse("Bad::directive:x")).isEmpty();
        assertThat(Urn.parse("::directive:x")).isEmpty();
        assertThat(Urn.parse("a::b::directive:x")).isEmpty();
        assertThat(Urn.parse("writing::unknown:x")).isEmpty();
        assertThat(Urn.parse("QA_001")).isEmpty();
        assertThat(Urn.parse("profile:x")).isEmpty();
        assertThat(Urn.parse("directive:")).isEmpty();
        assertThat(Urn.parse(":x")).isEmpty();
    }
}
