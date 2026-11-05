package ai.speckitty.charter.kernel.transport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The one entity-tag parser of the service: the strong comparison an {@code If-Match} takes (RFC 9110 &sect;13.1.1)
 * and the weak one an {@code If-None-Match} takes (&sect;13.1.2). The {@code matches} cases are the ones
 * {@code PackRevisionTest} and the registration controllers pinned before the parser left {@code PackRevision}.
 */
@DisplayName("EntityTags")
class EntityTagsTest {

    private static final String CURRENT = "sha256:" + "a".repeat(64);
    private static final String OTHER = "sha256:" + "0".repeat(64);

    @Test
    @DisplayName("matches() is true only for the current token")
    void matchesOnlyTheCurrentToken() {
        assertThat(EntityTags.matches(CURRENT, CURRENT)).isTrue();
        assertThat(EntityTags.matches(OTHER, CURRENT)).isFalse();
    }

    @Test
    @DisplayName("matches() is false when no If-Match was sent")
    void nullMatchesNothing() {
        assertThat(EntityTags.matches(null, CURRENT)).isFalse();
        assertThat(EntityTags.matches("", CURRENT)).isFalse();
        assertThat(EntityTags.matches(" , ", CURRENT)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", " * ", "\t*\t"})
    @DisplayName("matches() is true for the wildcard, which names any existing resource")
    void aWildcardMatches(String ifMatch) {
        assertThat(EntityTags.matches(ifMatch, CURRENT)).isTrue();
        assertThat(EntityTags.isWildcard(ifMatch)).isTrue();
    }

    @Test
    @DisplayName("matches() is true for a quoted strong tag")
    void aQuotedStrongTagMatches() {
        assertThat(EntityTags.matches(EntityTags.quote(CURRENT), CURRENT)).isTrue();
    }

    @Test
    @DisplayName("matches() is true for a list when one strong member is the current token")
    void aListMatchesWhenOneStrongMemberIsCurrent() {
        assertThat(EntityTags.matches("\"sha256:stale\", " + EntityTags.quote(CURRENT), CURRENT))
                .isTrue();
        assertThat(EntityTags.matches(", ,\t" + EntityTags.quote(CURRENT) + ",", CURRENT))
                .isTrue();
        assertThat(EntityTags.matches("\"sha256:stale\", \"other\"", CURRENT)).isFalse();
    }

    @Test
    @DisplayName("matches() is never true for a weak member")
    void aWeakMemberNeverMatches() {
        assertThat(EntityTags.matches("W/" + EntityTags.quote(CURRENT), CURRENT))
                .isFalse();
        assertThat(EntityTags.matches("W/" + EntityTags.quote(CURRENT) + ", \"other\"", CURRENT))
                .isFalse();
        assertThat(EntityTags.matches("W/" + CURRENT, CURRENT)).isFalse();
    }

    @Test
    @DisplayName("matches() compares an unquoted member as its bare text")
    void anUnquotedMemberComparesAsBareText() {
        assertThat(EntityTags.matches(" " + CURRENT + " ", CURRENT)).isTrue();
        assertThat(EntityTags.matches("other, " + CURRENT, CURRENT)).isTrue();
        assertThat(EntityTags.matches(OTHER, CURRENT)).isFalse();
    }

    @Test
    @DisplayName("matches() is false once a member's quote is never closed")
    void anUnterminatedQuoteMatchesNothing() {
        assertThat(EntityTags.matches("\"" + CURRENT, CURRENT)).isFalse();
        assertThat(EntityTags.matches("\"other\", \"unterminated, " + CURRENT, CURRENT))
                .isFalse();
    }

    @Test
    @DisplayName("noneMatch() compares weakly: a weak member matches its opaque tag")
    void noneMatchComparesWeakly() {
        assertThat(EntityTags.noneMatch(EntityTags.quote(CURRENT), CURRENT)).isTrue();
        assertThat(EntityTags.noneMatch("W/" + EntityTags.quote(CURRENT), CURRENT))
                .isTrue();
        assertThat(EntityTags.noneMatch("\"other\", W/\"x\" ,  " + EntityTags.quote(CURRENT), CURRENT))
                .isTrue();
        assertThat(EntityTags.noneMatch("W/\"other\"", CURRENT)).isFalse();
    }

    @Test
    @DisplayName("noneMatch() is true for the wildcard and false when nothing was sent")
    void noneMatchWildcardAndAbsence() {
        assertThat(EntityTags.noneMatch("*", CURRENT)).isTrue();
        assertThat(EntityTags.noneMatch(null, CURRENT)).isFalse();
        assertThat(EntityTags.noneMatch("W/\"" + CURRENT, CURRENT)).isFalse();
    }

    @Test
    @DisplayName("isWildcard() is false for anything but a lone *")
    void onlyALoneStarIsTheWildcard() {
        assertThat(EntityTags.isWildcard(null)).isFalse();
        assertThat(EntityTags.isWildcard("**")).isFalse();
        assertThat(EntityTags.isWildcard("*, \"x\"")).isFalse();
    }

    @Test
    @DisplayName("quote() wraps the token in double quotes, as the ETag header carries it")
    void quoteWrapsTheToken() {
        assertThat(EntityTags.quote(CURRENT)).isEqualTo("\"" + CURRENT + "\"");
    }
}
