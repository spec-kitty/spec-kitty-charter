package ai.speckitty.charter.kernel.transport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The header names are wire vocabulary: a rename is a contract change, so the literals are pinned here. */
class HeaderNamesTest {

    @Test
    void theSourceFingerprintHeaderIsNamedAsTheContractNamesIt() {
        assertThat(HeaderNames.SOURCE_FINGERPRINT).isEqualTo("Charter-Source-Fingerprint");
    }

    @Test
    void theContractVersionHeaderIsNamedAsTheContractNamesIt() {
        assertThat(HeaderNames.CONTRACT_VERSION).isEqualTo("Charter-Contract-Version");
    }
}
