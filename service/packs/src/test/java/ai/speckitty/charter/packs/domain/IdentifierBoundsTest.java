package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pack identifiers are capped before they are matched (#440): 64 for kebab ids, 255 for an executable entry. That they
 * match possessively, so no length overflows the stack, is pinned by {@code RegexRecursionGuardTest} in build-logic.
 */
@DisplayName("Pack identifiers are capped at 64 (255 for an executable entry)")
class IdentifierBoundsTest {

    private static void newPreset(String id) {
        new Preset(id, "a preset", Map.of());
    }

    private static void newExecutable(String entry) {
        new Executable(ExecutableRuntime.PYTHON, Optional.empty(), entry, List.of());
    }

    static Stream<Arguments> kebabTypes() {
        return Stream.of(
                Arguments.of("GuardIdentifier", (Consumer<String>) GuardIdentifier::new),
                Arguments.of("ProcedureIdentifier", (Consumer<String>) ProcedureIdentifier::new),
                Arguments.of("ToolguideIdentifier", (Consumer<String>) ToolguideIdentifier::new),
                Arguments.of("SkillIdentifier", (Consumer<String>) SkillIdentifier::new),
                Arguments.of("Preset id", (Consumer<String>) IdentifierBoundsTest::newPreset));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kebabTypes")
    @DisplayName("a kebab id of exactly 64 characters is accepted and one of 65 is refused")
    void aKebabIdIsCappedAt64(String type, Consumer<String> construct) {
        String atCap = "a" + "b".repeat(63);
        assertThatCode(() -> construct.accept(atCap)).doesNotThrowAnyException();
        assertThatThrownBy(() -> construct.accept(atCap + "c")).isInstanceOf(ConstraintBreachException.class);
    }

    static Stream<Arguments> capped64Types() {
        return Stream.of(
                Arguments.of("DirectiveIdentifier", "D" + "A".repeat(63), (Consumer<String>) DirectiveIdentifier::new),
                Arguments.of("TacticIdentifier", "t" + "b".repeat(63), (Consumer<String>) TacticIdentifier::new),
                Arguments.of("ParadigmIdentifier", "p" + "b".repeat(63), (Consumer<String>) ParadigmIdentifier::new),
                Arguments.of(
                        "StyleguideIdentifier", "s" + "b".repeat(63), (Consumer<String>) StyleguideIdentifier::new));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("capped64Types")
    @DisplayName("a directive, tactic, paradigm or styleguide id of exactly 64 characters is accepted and 65 refused")
    void aReferenceKindIdIsCappedAt64(String type, String atCap, Consumer<String> construct) {
        assertThat(atCap).hasSize(64);
        assertThatCode(() -> construct.accept(atCap)).doesNotThrowAnyException();
        assertThatThrownBy(() -> construct.accept(atCap + atCap.charAt(1)))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("an executable entry of exactly 255 characters is accepted and one of 256 is refused")
    void anExecutableEntryIsCappedAt255() {
        String atCap = "bin/" + "t".repeat(251);
        assertThat(atCap).hasSize(255);
        assertThatCode(() -> newExecutable(atCap)).doesNotThrowAnyException();
        assertThatThrownBy(() -> newExecutable(atCap + "x")).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("an asset-backed guard's pack name is capped at 64 and still captured whole at the cap")
    void aGuardImplementationPackIsCappedAt64() {
        String pack = "q" + "a".repeat(63);
        assertThat(GuardImplementation.parse(pack + "::asset:lint"))
                .isEqualTo(new GuardImplementation.AssetBacked(Optional.of(pack), "lint"));
        assertThatThrownBy(() -> GuardImplementation.parse(pack + "x::asset:lint"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longer than 64")
                .hasMessageNotContaining(pack);
        // The cap is checked before the spelling is matched, so an over-long qualifier is refused for its length even
        // when the rest of the text is no spelling at all, and the refusal never echoes the text.
        assertThatThrownBy(() -> GuardImplementation.parse(pack + "x::asset:!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longer than 64")
                .hasMessageNotContaining(pack);
    }

    static Stream<Arguments> kebabVerdicts() {
        // The grammar's own verdicts are IdentifierGrammarTest's; these say which grammar each type delegates to.
        return Stream.of(
                Arguments.of("a-b", true, true), Arguments.of("1a", true, false), Arguments.of("a--b", false, false));
    }

    @ParameterizedTest(name = "\"{0}\": digit-first kinds {1}, letter-first kinds {2}")
    @MethodSource("kebabVerdicts")
    @DisplayName("short kebab ids keep their verdict")
    void shortKebabIdsKeepTheirVerdict(String id, boolean digitFirstValid, boolean letterFirstValid) {
        assertVerdict(GuardIdentifier::new, id, digitFirstValid);
        assertVerdict(ProcedureIdentifier::new, id, digitFirstValid);
        assertVerdict(ToolguideIdentifier::new, id, letterFirstValid);
        assertVerdict(SkillIdentifier::new, id, letterFirstValid);
        if (!id.isEmpty()) {
            assertVerdict(IdentifierBoundsTest::newPreset, id, letterFirstValid);
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
