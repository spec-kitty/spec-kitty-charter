package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.resolver.ScalarResolver;

/**
 * The dump schema's YAML 1.1 int and float resolvers judge a scalar of any length (#441).
 *
 * <p>Their sexagesimal branch {@code (?::[0-5]?[0-9])+} recursed once per segment, so writing a string such as
 * {@code 1:5:5:…} tens of thousands of characters long threw {@link StackOverflowError}. The branch is possessive now
 * and resolves every scalar as it did before.
 */
@DisplayName("The dump schema resolves a long sexagesimal scalar without overflowing, and short scalars as before")
class YamlResolverBoundsTest {

    private static final int SEGMENTS = 10_000;
    private static final ScalarResolver RESOLVER = Yaml.dumpSchema().getScalarResolver();

    private static Tag resolve(String scalar) {
        return RESOLVER.resolve(scalar, Boolean.TRUE);
    }

    @Test
    @DisplayName("a 20 001-character sexagesimal integer resolves as an int, not a StackOverflowError")
    void aLongSexagesimalIntResolvesAsAnInt() {
        assertThat(resolve("1" + ":5".repeat(SEGMENTS))).isEqualTo(Tag.INT);
    }

    @Test
    @DisplayName("a 20 003-character sexagesimal float resolves as a float, not a StackOverflowError")
    void aLongSexagesimalFloatResolvesAsAFloat() {
        assertThat(resolve("1" + ":5".repeat(SEGMENTS) + ".5")).isEqualTo(Tag.FLOAT);
    }

    static Stream<Arguments> shortScalars() {
        // Tags pinned from the resolver before the possessive rewrite.
        return Stream.of(
                Arguments.of("1", Tag.INT),
                Arguments.of("-1", Tag.INT),
                Arguments.of("1_000", Tag.INT),
                Arguments.of("0x1F", Tag.INT),
                Arguments.of("0b101", Tag.INT),
                Arguments.of("017", Tag.INT),
                Arguments.of("1:30", Tag.INT),
                Arguments.of("190:20:30", Tag.INT),
                Arguments.of("1:60", Tag.STR),
                Arguments.of("1::30", Tag.STR),
                Arguments.of("1.5", Tag.FLOAT),
                Arguments.of("1:30.5", Tag.FLOAT),
                Arguments.of("190:20:30.15", Tag.FLOAT),
                Arguments.of(".inf", Tag.FLOAT),
                Arguments.of(".nan", Tag.FLOAT),
                Arguments.of("abc", Tag.STR),
                Arguments.of("1:", Tag.STR),
                Arguments.of(":30", Tag.STR));
    }

    @ParameterizedTest(name = "\"{0}\" resolves as {1}")
    @MethodSource("shortScalars")
    @DisplayName("short scalars resolve as before")
    void shortScalarsResolveAsBefore(String scalar, Tag expected) {
        assertThat(resolve(scalar)).isEqualTo(expected);
    }
}
