package ai.speckitty.charter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The service version the build writes is the root project's {@code version} from {@code gradle.properties}, a
 * semantic version as the contract's pattern asks. It is compared with that version, not with the contract version:
 * the two are independent and may be equal (both are {@code 0.1.0-SNAPSHOT} while the contract is 0.x).
 */
class ServiceVersionTest {

    private static final String SEMVER = "(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(-[0-9A-Za-z.-]+)?";

    @Test
    void theBuildWritesTheProjectVersionAsASemanticVersion() {
        assertThat(ServiceVersion.value()).matches(SEMVER);
        assertThat(ServiceVersion.value()).isEqualTo(System.getProperty("charter.projectVersion"));
    }
}
