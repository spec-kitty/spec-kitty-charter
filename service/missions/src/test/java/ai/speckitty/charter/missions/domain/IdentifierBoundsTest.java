package ai.speckitty.charter.missions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Mission identifiers are capped before they are matched (#440): 64 for kebab ids (each part of a guard reference),
 * 255 for an expected artifact's dotted key. That they match possessively, so no length overflows the stack, is pinned
 * by {@code RegexRecursionGuardTest} in build-logic.
 */
@DisplayName("Mission identifiers are capped at 64 (255 for an artifact key)")
class IdentifierBoundsTest {

    private static final String AT_64 = "a" + "b".repeat(63);

    static Stream<Arguments> kebabTypes() {
        return Stream.of(
                Arguments.of("LifecycleIdentifier", (Consumer<String>) LifecycleIdentifier::new),
                Arguments.of("MissionTypeIdentifier", (Consumer<String>) MissionTypeIdentifier::new),
                Arguments.of("MissionStep id", (Consumer<String>) IdentifierBoundsTest::step));
    }

    private static void step(String id) {
        new MissionStep(
                id,
                "Step",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kebabTypes")
    @DisplayName("a kebab id of exactly 64 characters is accepted and one of 65 is refused")
    void aKebabIdIsCappedAt64(String type, Consumer<String> construct) {
        assertThatCode(() -> construct.accept(AT_64)).doesNotThrowAnyException();
        assertThatThrownBy(() -> construct.accept(AT_64 + "c")).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a guard reference is captured whole at the caps; a part over 64, or a total over 136, is refused")
    void aGuardReferenceIsCappedAtItsPartsAndItsTotal() {
        String atLimit = AT_64 + "::guard:" + AT_64;
        assertThat(atLimit).hasSize(136);
        assertThat(GuardReference.parse(atLimit)).isEqualTo(new GuardReference(Optional.of(AT_64), AT_64));
        assertThat(GuardReference.parse(AT_64)).isEqualTo(new GuardReference(Optional.empty(), AT_64));
        assertThatThrownBy(() -> GuardReference.parse(AT_64 + "c")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GuardReference.parse(AT_64 + "c::guard:review"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GuardReference.parse(atLimit + "c"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not longer than 136");
    }

    @Test
    @DisplayName("a dotted key of exactly 255 characters is dotted and one of 256 is not")
    void anArtifactKeyIsCappedAt255() {
        String atCap = "spec." + "m".repeat(250);
        assertThat(atCap).hasSize(255);
        assertThat(ExpectedArtifact.isDottedKey(atCap)).isTrue();
        assertThat(ExpectedArtifact.isDottedKey(atCap + "d")).isFalse();
    }

    static Stream<Arguments> kebabVerdicts() {
        // The grammar's own verdicts are IdentifierGrammarTest's; these say which grammar each type delegates to.
        return Stream.of(
                Arguments.of("a-b", true, true), Arguments.of("1a", true, false), Arguments.of("a--b", false, false));
    }

    @ParameterizedTest(name = "\"{0}\": lifecycle {1}, mission type {2}")
    @MethodSource("kebabVerdicts")
    @DisplayName("short kebab ids keep their verdict")
    void shortKebabIdsKeepTheirVerdict(String id, boolean lifecycleValid, boolean missionTypeValid) {
        assertVerdict(LifecycleIdentifier::new, id, lifecycleValid);
        assertVerdict(MissionTypeIdentifier::new, id, missionTypeValid);
    }

    static Stream<Arguments> guardReferenceVerdicts() {
        return Stream.of(
                Arguments.of("review", Optional.empty(), "review"),
                Arguments.of("1-review", Optional.empty(), "1-review"),
                Arguments.of("qa::guard:lint", Optional.of("qa"), "lint"),
                Arguments.of("qa-pack::guard:trace-ready", Optional.of("qa-pack"), "trace-ready"));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @MethodSource("guardReferenceVerdicts")
    @DisplayName("short guard references keep their captured parts")
    void shortGuardReferencesKeepTheirParts(String text, Optional<String> pack, String id) {
        assertThat(GuardReference.parse(text)).isEqualTo(new GuardReference(pack, id));
    }

    @Test
    @DisplayName("malformed short guard references stay refused")
    void malformedGuardReferencesStayRefused() {
        for (String text : new String[] {"", "qa::guard:", "::guard:x", "qa:guard:x", "qa::lint", "Review"}) {
            assertThatThrownBy(() -> GuardReference.parse(text)).as(text).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static void assertVerdict(Consumer<String> construct, String text, boolean valid) {
        if (valid) {
            assertThatCode(() -> construct.accept(text)).as(text).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> construct.accept(text)).as(text).isInstanceOf(ConstraintBreachException.class);
        }
    }
}
