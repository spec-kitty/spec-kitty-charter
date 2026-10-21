package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrnTest {

    @Test
    void parsesKnownKinds() {
        assertThat(Urn.parse("action:software-dev/plan")).contains(new Urn(NodeKind.ACTION, "software-dev/plan"));
        assertThat(Urn.parse("directive:DIRECTIVE_010").map(Urn::toString)).contains("directive:DIRECTIVE_010");
    }

    @Test
    void refusesWhatIsNotAUrn() {
        assertThat(Urn.parse("QA_001")).isEmpty();
        assertThat(Urn.parse("profile:x")).isEmpty();
        assertThat(Urn.parse("directive:")).isEmpty();
        assertThat(Urn.parse(":x")).isEmpty();
    }
}
