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
 * Mission identifiers of any length are judged, never overflow the stack, and are capped (#440).
 *
 * <p>The repeated group in each grammar made the regex engine recurse once per repetition, so an id of about 10 000
 * characters threw {@link StackOverflowError}. Each type now matches possessively and checks a cap first: 64 for kebab
 * ids (each part of a guard reference), 255 for an expected artifact's dotted key.
 */
@DisplayName("Mission identifiers are bounded: no overflow on long input, capped at 64 (255 for an artifact key)")
class IdentifierBoundsTest {

    private static final int SEGMENTS = 10_000;
    private static final String LONG_KEBAB = "a" + "-a".repeat(SEGMENTS);
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
    @DisplayName("a 20 001-character kebab id is refused as a constraint breach, not a StackOverflowError")
    void aVeryLongKebabIdIsRefused(String type, Consumer<String> construct) {
        assertThatThrownBy(() -> construct.accept(LONG_KEBAB)).isInstanceOf(ConstraintBreachException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kebabTypes")
    @DisplayName("a kebab id of exactly 64 characters is accepted and one of 65 is refused")
    void aKebabIdIsCappedAt64(String type, Consumer<String> construct) {
        assertThatCode(() -> construct.accept(AT_64)).doesNotThrowAnyException();
        assertThatThrownBy(() -> construct.accept(AT_64 + "c")).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a guard reference with a 20 001-character id or pack part is refused, not a StackOverflowError")
    void aVeryLongGuardReferenceIsRefused() {
        assertThatThrownBy(() -> GuardReference.parse(LONG_KEBAB)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GuardReference.parse(LONG_KEBAB + "::guard:review"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("each part of a guard reference is capped at 64 and still captured whole at the cap")
    void eachGuardReferencePartIsCappedAt64() {
        assertThat(GuardReference.parse(AT_64 + "::guard:" + AT_64))
                .isEqualTo(new GuardReference(Optional.of(AT_64), AT_64));
        assertThat(GuardReference.parse(AT_64)).isEqualTo(new GuardReference(Optional.empty(), AT_64));
        assertThatThrownBy(() -> GuardReference.parse(AT_64 + "c")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GuardReference.parse(AT_64 + "c::guard:review"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a guard reference longer than two parts and the separator is refused before matching")
    void anOverlongGuardReferenceIsRefusedBeforeMatching() {
        String atLimit = AT_64 + "::guard:" + AT_64;
        assertThat(GuardReference.parse(atLimit)).isEqualTo(new GuardReference(Optional.of(AT_64), AT_64));
        assertThatThrownBy(() -> GuardReference.parse(atLimit + "c"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longer than");
    }

    @Test
    @DisplayName("a 20 001-character dotted key is not dotted, not a StackOverflowError; the cap is 255")
    void anArtifactKeyIsBoundedAndCappedAt255() {
        assertThat(ExpectedArtifact.isDottedKey("a" + ".a".repeat(SEGMENTS))).isFalse();
        String atCap = "spec." + "m".repeat(250);
        assertThat(atCap).hasSize(255);
        assertThat(ExpectedArtifact.isDottedKey(atCap)).isTrue();
        assertThat(ExpectedArtifact.isDottedKey(atCap + "d")).isFalse();
    }

    static Stream<Arguments> kebabVerdicts() {
        // Verdicts pinned from the patterns before the possessive rewrite.
        return Stream.of(
                Arguments.of("", false, false),
                Arguments.of("-", false, false),
                Arguments.of("a-", false, false),
                Arguments.of("-a", false, false),
                Arguments.of("a--b", false, false),
                Arguments.of("A", false, false),
                Arguments.of("a_b", false, false),
                Arguments.of("a-b", true, true),
                Arguments.of("a1-2b-c3", true, true),
                Arguments.of("1a", true, false),
                Arguments.of("1-a", true, false));
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

    static Stream<Arguments> keyVerdicts() {
        return Stream.of(
                Arguments.of("spec.md", true),
                Arguments.of("tasks.wp-01.md", true),
                Arguments.of("a.b_c", true),
                Arguments.of("spec", false),
                Arguments.of("Spec.md", false),
                Arguments.of("spec.", false),
                Arguments.of(".md", false),
                Arguments.of("spec..md", false),
                Arguments.of("1spec.md", false));
    }

    @ParameterizedTest(name = "\"{0}\" dotted: {1}")
    @MethodSource("keyVerdicts")
    @DisplayName("short artifact keys keep their verdict")
    void shortArtifactKeysKeepTheirVerdict(String key, boolean dotted) {
        assertThat(ExpectedArtifact.isDottedKey(key)).as(key).isEqualTo(dotted);
    }

    private static void assertVerdict(Consumer<String> construct, String text, boolean valid) {
        if (valid) {
            assertThatCode(() -> construct.accept(text)).as(text).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> construct.accept(text)).as(text).isInstanceOf(ConstraintBreachException.class);
        }
    }
}
