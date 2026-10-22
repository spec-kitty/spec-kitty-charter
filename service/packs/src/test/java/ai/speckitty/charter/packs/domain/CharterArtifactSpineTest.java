package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CharterArtifactSpineTest {

    @Test
    void enforcementTokensRoundTripIncludingTheHyphenatedOne() {
        for (Enforcement value : Enforcement.values()) {
            assertThat(Enforcement.fromToken(value.token())).contains(value);
        }
        assertThat(Enforcement.LENIENT_ADHERENCE.token()).isEqualTo("lenient-adherence");
        assertThat(Enforcement.fromToken("lenient_adherence")).isEmpty();
    }
}
