package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The identifier grammars packs and missions share (#444): what each accepts, the cap each checks before matching,
 * and that a very long text is refused instead of overflowing the stack (#440).
 */
@DisplayName("The shared identifier grammars")
class IdentifierGrammarTest {

    static Stream<Arguments> slugVerdicts() {
        return Stream.of(
                // text, isSlug, isLetterFirstSlug
                Arguments.of("a", true, true),
                Arguments.of("a-b", true, true),
                Arguments.of("abc-123-x9", true, true),
                Arguments.of("1a", true, false),
                Arguments.of("1-2", true, false),
                Arguments.of("", false, false),
                Arguments.of("-a", false, false),
                Arguments.of("a-", false, false),
                Arguments.of("a--b", false, false),
                Arguments.of("A", false, false),
                Arguments.of("a_b", false, false),
                Arguments.of("a.b", false, false),
                Arguments.of("a b", false, false));
    }

    @ParameterizedTest(name = "\"{0}\": slug {1}, letter-first slug {2}")
    @MethodSource("slugVerdicts")
    void slugsKeepTheirVerdict(String text, boolean slug, boolean letterFirst) {
        assertThat(IdentifierGrammar.isSlug(text)).isEqualTo(slug);
        assertThat(IdentifierGrammar.isLetterFirstSlug(text)).isEqualTo(letterFirst);
    }

    static Stream<Arguments> dottedKeyVerdicts() {
        return Stream.of(
                Arguments.of("spec.md", true),
                Arguments.of("plan-doc.section_1.part-2", true),
                Arguments.of("a.b", true),
                Arguments.of("a", false),
                Arguments.of("a.", false),
                Arguments.of(".a", false),
                Arguments.of("1a.b", false),
                Arguments.of("a..b", false),
                Arguments.of("a.B", false),
                Arguments.of("a_x.b", false));
    }

    @ParameterizedTest(name = "\"{0}\": {1}")
    @MethodSource("dottedKeyVerdicts")
    void dottedKeysKeepTheirVerdict(String text, boolean verdict) {
        assertThat(IdentifierGrammar.isDottedKey(text)).isEqualTo(verdict);
    }

    static Stream<Arguments> relativePathVerdicts() {
        return Stream.of(
                Arguments.of("lint.py", true),
                Arguments.of("scripts/lint.py", true),
                Arguments.of("_a/b-c/D.e", true),
                Arguments.of("", false),
                Arguments.of("/abs/lint.py", false),
                Arguments.of("../lint.py", false),
                Arguments.of("a/../b", false),
                Arguments.of(".hidden/lint.py", false),
                Arguments.of("a//b", false),
                Arguments.of("a/", false),
                Arguments.of("a b", false),
                Arguments.of("a\\b", false));
    }

    @ParameterizedTest(name = "\"{0}\": {1}")
    @MethodSource("relativePathVerdicts")
    void relativePathsKeepTheirVerdict(String text, boolean verdict) {
        assertThat(IdentifierGrammar.isRelativePath(text)).isEqualTo(verdict);
    }

    static Stream<Arguments> capped() {
        return Stream.of(
                Arguments.of("slug", (Predicate<String>) IdentifierGrammar::isSlug, "a", 64),
                Arguments.of("letter-first slug", (Predicate<String>) IdentifierGrammar::isLetterFirstSlug, "a", 64),
                Arguments.of("dotted key", (Predicate<String>) IdentifierGrammar::isDottedKey, "a.", 255),
                Arguments.of("relative path", (Predicate<String>) IdentifierGrammar::isRelativePath, "a", 255));
    }

    @ParameterizedTest(name = "a {0} is capped at {3}")
    @MethodSource("capped")
    void eachGrammarIsCappedBeforeItMatches(String name, Predicate<String> grammar, String head, int cap) {
        String atCap = head + "a".repeat(cap - head.length());
        assertThat(grammar.test(atCap)).as("%d characters", cap).isTrue();
        assertThat(grammar.test(atCap + "a")).as("%d characters", cap + 1).isFalse();
    }

    static Stream<Arguments> grammars() {
        return Stream.of(
                Arguments.of("slug", IdentifierGrammar.SLUG, "-a"),
                Arguments.of("letter-first slug", IdentifierGrammar.LETTER_FIRST_SLUG, "-a"),
                Arguments.of("dotted key", IdentifierGrammar.DOTTED_KEY, ".a"),
                Arguments.of("relative path", IdentifierGrammar.RELATIVE_PATH, "/a"));
    }

    /**
     * The grammar strings are what a caller composes into a pattern of its own (GuardReference, GuardImplementation),
     * so they are matched here without the cap: a backtracking repeat recurses once per repetition and throws
     * {@link StackOverflowError} on about 20 000 characters (#440), a possessive one does not.
     */
    @ParameterizedTest(name = "the {0} grammar matches a very long text without overflowing the stack")
    @MethodSource("grammars")
    void eachGrammarMatchesAVeryLongTextWithoutOverflowingTheStack(String name, String grammar, String repeat) {
        String text = "a" + repeat.repeat(10_000);
        assertThat(Pattern.compile(grammar).matcher(text).matches()).isTrue();
    }

    @Test
    void theCapsAreThoseThePackSchemasDeclare() {
        assertThat(IdentifierGrammar.MAX_SLUG_LENGTH).isEqualTo(64);
        assertThat(IdentifierGrammar.MAX_PATH_LENGTH).isEqualTo(255);
    }

    @Test
    void noGrammarAcceptsNull() {
        assertThat(IdentifierGrammar.isSlug(null)).isFalse();
        assertThat(IdentifierGrammar.isLetterFirstSlug(null)).isFalse();
        assertThat(IdentifierGrammar.isDottedKey(null)).isFalse();
        assertThat(IdentifierGrammar.isRelativePath(null)).isFalse();
    }
}
