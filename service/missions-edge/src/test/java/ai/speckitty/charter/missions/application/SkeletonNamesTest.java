package ai.speckitty.charter.missions.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * D9: a skeleton's one-segment name is its path under the type's {@code templates/} directory escaped as in RFC 6901,
 * {@code ~} as {@code ~0} first and then {@code /} as {@code ~1}; decoding undoes it in the reverse order. The mapping
 * is one to one, so a file whose name holds a literal {@code ~1} is not confused with a nested path.
 */
class SkeletonNamesTest {

    @ParameterizedTest(name = "{0} <-> {1}")
    @CsvSource({
        "spec-template.md, spec-template.md",
        "divio/howto-template.md, divio~1howto-template.md",
        "a~1b.md, a~01b.md",
        "a/b~c/d.md, a~1b~0c~1d.md",
        "~, ~0",
        "~~/, ~0~0~1"
    })
    void aPathIsNamedAndTheNameDecodesBackToIt(String path, String name) {
        assertThat(SkeletonNames.encode(path)).isEqualTo(name);
        assertThat(SkeletonNames.decode(name)).contains(path);
    }

    @ParameterizedTest
    @ValueSource(strings = {"divio~2howto-template.md", "a~", "~x", "a/b.md", ""})
    void aNameThatIsNoEscapingOfAPathDecodesToNothing(String name) {
        assertThat(SkeletonNames.decode(name)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a~1b.md", "divio/howto-template.md", "~0~1", "plain.md"})
    void nameAndDecodeRoundTripEveryPath(String path) {
        assertThat(SkeletonNames.decode(SkeletonNames.encode(path))).contains(path);
    }
}
