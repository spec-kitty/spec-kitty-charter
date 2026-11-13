package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The one media-type rule (research R6): a slash with text on both sides. It is the rule validate applies to an
 * asset's {@code mime} ({@code PackPolicyTest} pins the findings), and the payload read applies to its
 * {@code Content-Type}.
 */
class MediaTypesTest {

    @ParameterizedTest
    @ValueSource(
            strings = {"text/x-python", "image/png", "a/b", "application/vnd.x+json; charset=utf-8", "a/b/c", "x/ "})
    void aSlashWithTextOnBothSidesIsWellFormed(String mime) {
        assertThat(MediaTypes.wellFormed(mime)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"plain", "/png", "image/", "/", "//"})
    void aMimeWithoutTextOnBothSidesOfItsFirstSlashIsNot(String mime) {
        assertThat(MediaTypes.wellFormed(mime)).isFalse();
    }
}
