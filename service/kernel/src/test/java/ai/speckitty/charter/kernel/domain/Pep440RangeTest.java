package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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
        // spellings the unit table keeps beside admits.tsv: an epoched wildcard and a local segment's case and zeros
        "==1!1.*, 1!1.2, true",
        "==1.0+abc, 1.0+ABC, true",
        "==1.0+abc.1, 1.0+abc.01, true",
    })
    void aRangeAdmitsOrRefusesAVersion(String range, String version, boolean admitted) {
        assertThat(Pep440Range.admits(range.trim(), version.trim())).isEqualTo(admitted);
    }

    /** The rows {@code scripts/packs/pep440_corpus.py} had {@code packaging} decide: the range admits what it admits. */
    @ParameterizedTest
    @MethodSource("packagingVerdicts")
    void aRangeAdmitsExactlyWhatPackagingAdmits(String range, String version, boolean admitted) {
        assertThat(Pep440Range.admits(range, version)).isEqualTo(admitted);
    }

    static Stream<Arguments> packagingVerdicts() throws IOException {
        try (InputStream in = Pep440RangeTest.class.getResourceAsStream("/pep440/admits.tsv")) {
            assertThat(in).as("the admits corpus resource").isNotNull();
            List<Arguments> rows = new String(in.readAllBytes(), StandardCharsets.US_ASCII)
                    .lines()
                    .filter(line -> !line.startsWith("#") && !line.isEmpty())
                    .map(line -> line.split("\t"))
                    .map(cells -> Arguments.of(cells[0], cells[1], Boolean.parseBoolean(cells[2])))
                    .toList();
            assertThat(rows).hasSizeGreaterThan(500);
            return rows.stream();
        }
    }

    /** Whether two sets share a version: ranges that only touch share none. */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                ">=1.0,<2|>=2.0|false",
                "<2|>=2|false",
                "==1.2.*|>=1.3|false",
                ">=1.0,<3|>=2.0|true",
                "~=1.4.5|>=1.4|true",
                // a wildcard exclusion leaves the versions past the prefix: 2.0 satisfies both
                "!=1.*|>=1.0|true",
                // a prerelease of 1.0 sits above 1.0rc1 and below 1.0, as admits orders them
                ">1.0rc1|<1.0|true",
                // an epoch outranks the release, so no epoch-0 version reaches it
                ">=1!1|<5|false",
                // an identity clause names one text, which no ordering clause can admit
                "===foo|>=1|false",
                // numbers of any size are numbers, not an error
                ">=1,<99999999999|<5|true",
                // a local version is a different version for == when the operand names one
                "==1.0+abc|==1.0+xyz|false",
                "==1.0+abc|==1.0|true",
                "==1.0+abc|!=1.0+abc|false"
            })
    void twoRangesOverlapWhenSomeVersionSatisfiesBoth(String first, String second, boolean overlap) {
        assertThat(Pep440Range.overlaps(first, second)).isEqualTo(overlap);
        assertThat(Pep440Range.overlaps(second, first)).isEqualTo(overlap);
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
