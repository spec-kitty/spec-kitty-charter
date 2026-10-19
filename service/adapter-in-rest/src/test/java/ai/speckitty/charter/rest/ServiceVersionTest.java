package ai.speckitty.charter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The service version the build writes is the semantic version the contract's pattern asks for. */
class ServiceVersionTest {

    private static final String SEMVER = "(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(-[0-9A-Za-z.-]+)?";

    @Test
    void theBuildWritesASemanticVersionThatIsNotTheContractVersion() {
        assertThat(ServiceVersion.value()).matches(SEMVER);
        assertThat(ServiceVersion.value()).isNotEqualTo(ContractVersion.value());
    }
}
