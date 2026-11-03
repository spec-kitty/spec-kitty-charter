package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The range answers what a specifier set admits, in PEP 440's ordering. */
class Pep440RangeTest {

    @ParameterizedTest
    @CsvSource({
        "==1.2.3, 1.2.3, true",
        "==1.2.3, 1.2.4, false",
        "==1.2, 1.2.0, true",
        "!=1.0, 1.0.0, false",
        "!=1.0, 1.0.1, true",
        "==1.2.*, 1.2.9, true",
        "==1.2.*, 1.3.0, false",
        "~=1.4.5, 1.4.9, true",
        "~=1.4.5, 1.5.0, false",
        "~=1.4.5, 1.4.4, false",
        ">1.0, 1.0.1, true",
        ">1.0, 1.0.0, false",
        "<=2.0, 2.0.0, true",
        "<=2.0, 2.0.1, false",
        ">=1!1.0, 2.0, false",
        ">=2.0, 1!1.0, true",
    })
    void aRangeAdmitsOrRefusesAVersion(String range, String version, boolean admitted) {
        assertThat(Pep440Range.admits(range.trim(), version.trim())).isEqualTo(admitted);
    }

    @Test
    void aCompoundRangeRequiresEverySpecifier() {
        assertThat(Pep440Range.admits(">=1.2,<2", "1.4.0")).isTrue();
        assertThat(Pep440Range.admits(">=1.2,<2", "1.2.0")).isTrue();
        assertThat(Pep440Range.admits(">=1.2,<2", "2.0.0")).isFalse();
        assertThat(Pep440Range.admits(">=1.2,<2", "1.1.9")).isFalse();
    }

    @Test
    void aPrereleaseSortsBeforeItsRelease() {
        assertThat(Pep440Range.admits("<1.0", "1.0a1")).isTrue();
        assertThat(Pep440Range.admits(">=1.0", "1.0a1")).isFalse();
        assertThat(Pep440Range.admits(">=1.0a1,<1.0b1", "1.0a2")).isTrue();
    }

    @Test
    void aDevelopmentReleaseSortsBeforeItsPrerelease() {
        assertThat(Pep440Range.admits("<1.0a1", "1.0.dev1")).isTrue();
        assertThat(Pep440Range.admits(">=1.0.dev1,<1.0a1", "1.0.dev2")).isTrue();
    }

    @Test
    void aPostReleaseSortsAfterItsRelease() {
        assertThat(Pep440Range.admits(">1.0", "1.0.post1")).isTrue();
        assertThat(Pep440Range.admits("<1.0.post1", "1.0")).isTrue();
    }

    @Test
    void anEpochOutranksTheReleaseItPrefixes() {
        assertThat(Pep440Range.admits(">2.0", "1!1.0")).isTrue();
        assertThat(Pep440Range.admits(">2.0", "0!3.0")).isTrue();
    }

    @Test
    void aMalformedVersionIsRejected() {
        assertThatThrownBy(() -> Pep440Range.admits(">=1.0", "not-a-version"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMalformedSpecifierIsRejected() {
        assertThatThrownBy(() -> Pep440Range.admits("~1.0", "1.0")).isInstanceOf(IllegalArgumentException.class);
    }
}
