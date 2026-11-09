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

    /** A valid kebab id of 20 001 characters: far over any cap and deep enough to overflow a recursive match. */
    private static final String LONG_KEBAB = "a" + "-a".repeat(10_000);

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
        assertThatThrownBy(() -> GuardImplementation.parse(LONG_KEBAB + "::asset:lint"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longer than 64");
    }

    static Stream<Arguments> kebabVerdicts() {
        // Verdicts pinned from the pattern before the possessive rewrite: the language below the cap is unchanged.
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

    static Stream<Arguments> entryVerdicts() {
        return Stream.of(
                Arguments.of("bin/tool.sh", true),
                Arguments.of("lint.py", true),
                Arguments.of("a/b/c", true),
                Arguments.of("_x/y-z.v1", true),
                Arguments.of("a/", false),
                Arguments.of("/a", false),
                Arguments.of("a//b", false),
                Arguments.of(".hidden/x", false),
                Arguments.of("a/.b", false),
                Arguments.of("a b", false));
    }

    @ParameterizedTest(name = "\"{0}\" valid: {1}")
    @MethodSource("entryVerdicts")
    @DisplayName("short executable entries keep their verdict")
    void shortExecutableEntriesKeepTheirVerdict(String entry, boolean valid) {
        assertVerdict(IdentifierBoundsTest::newExecutable, entry, valid);
    }

    private static void assertVerdict(Consumer<String> construct, String text, boolean valid) {
        if (valid) {
            assertThatCode(() -> construct.accept(text)).as(text).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> construct.accept(text)).as(text).isInstanceOf(ConstraintBreachException.class);
        }
    }
}
