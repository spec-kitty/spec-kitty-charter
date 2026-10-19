package ai.speckitty.charter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorktreeTokenTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";

    private final WorktreeToken token = WorktreeToken.of(TOKEN);

    @Test
    void theTokenItWasMadeFromMatches() {
        assertThat(token.matches(TOKEN)).isTrue();
    }

    @Test
    void aTokenOfTheSameLengthThatDiffersInOneCharacterDoesNotMatch() {
        assertThat(token.matches("0123456789abcdef0123456789abcdef0124")).isFalse();
    }

    @Test
    void aTokenThatDiffersOnlyInCaseDoesNotMatch() {
        assertThat(token.matches(TOKEN.replace('a', 'A'))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"", " ", "0", "0123456789abcdef0123456789abcdef012", "0123456789abcdef0123456789abcdef01234"})
    void aTokenOfAnotherLengthDoesNotMatch(String presented) {
        assertThat(token.matches(presented)).isFalse();
    }

    @Test
    void aTokenWithTheRightPrefixOrSuffixOnlyDoesNotMatch() {
        assertThat(token.matches(TOKEN + TOKEN)).isFalse();
        assertThat(token.matches(TOKEN + " ")).isFalse();
        assertThat(token.matches(" " + TOKEN)).isFalse();
    }

    @Test
    void noCredentialDoesNotMatch() {
        assertThat(token.matches(null)).isFalse();
    }

    @Test
    void twoHoldersOfDifferentTokensDoNotMatchEachOthersToken() {
        WorktreeToken other = WorktreeToken.of("another-token-another-token-another-token");
        assertThat(other.matches(TOKEN)).isFalse();
        assertThat(other.matches("another-token-another-token-another-token")).isTrue();
    }

    @Test
    void aMultiByteTokenMatchesByItsUtf8Bytes() {
        String unicode = "tökén-tökén-tökén-tökén-tökén-tökén";
        assertThat(WorktreeToken.of(unicode).matches(unicode)).isTrue();
        assertThat(WorktreeToken.of(unicode).matches(unicode.replace('ö', 'o'))).isFalse();
    }

    @Test
    void itPrintsRedactedAndNeverTheTokenNorItsDigest() {
        assertThat(token).hasToString("WorktreeToken[redacted]");
        assertThat(String.valueOf(token)).doesNotContain(TOKEN);
        assertThat(String.format("%s %s", token, token)).doesNotContain(TOKEN);
    }

    @Test
    void itHoldsNoFieldThatIsTheTokenText() {
        for (java.lang.reflect.Field field : WorktreeToken.class.getDeclaredFields()) {
            assertThat(field.getType())
                    .as(field.getName())
                    .isNotEqualTo(String.class)
                    .isNotEqualTo(char[].class);
        }
    }
}
