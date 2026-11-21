package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RoleBearer;
import ai.speckitty.charter.kernel.markdown.SlotId;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
@DisplayName(
        "Pack identifiers are on the shared grammar and capped at 64 (63 for a pack id, 255 for an executable entry)")
class IdentifierBoundsTest {

    private static void newPreset(String id) {
        new Preset(id, "a preset", Map.of());
    }

    private static void newProcedureStepRole(String role) {
        new ProcedureStep("Act", Optional.empty(), Optional.of(role), Optional.empty(), Optional.empty());
    }

    private static void newRolePin(String role) {
        new RolePins(Map.of(role, "implementer-ivan"));
    }

    private static void newStepEnhancementSlot(String slot) {
        new StepEnhancement(
                "spdd-reasons",
                new PackIdentifier("main"),
                StepEnhancement.SCHEMA_VERSION,
                List.of(new StepEnhancement.Entry(
                        slot,
                        new PromptFragment(
                                "reasons.prompt-fragment.md",
                                new MarkdownDocument(Optional.empty(), Optional.empty(), "Fill it.", List.of())))));
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
                Arguments.of("GlossaryContextIdentifier", (Consumer<String>) GlossaryContextIdentifier::new),
                Arguments.of("TermIdentifier", (Consumer<String>) TermIdentifier::new),
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
    @DisplayName("an asset-backed guard's pack name is a pack id, capped at 63 and still captured whole at the cap")
    void aGuardImplementationPackIsCappedAt63() {
        String pack = "q" + "a".repeat(PackIdentifier.MAX_LENGTH - 1);
        assertThat(GuardImplementation.parse(pack + "::asset:lint"))
                .isEqualTo(new GuardImplementation.AssetBacked(Optional.of(pack), "lint"));
        assertThatThrownBy(() -> GuardImplementation.parse(pack + "x::asset:lint"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longer than 63")
                .hasMessageNotContaining(pack);
        // The cap is checked before the spelling is matched, so an over-long qualifier is refused for its length even
        // when the rest of the text is no spelling at all, and the refusal never echoes the text.
        assertThatThrownBy(() -> GuardImplementation.parse(pack + "x::asset:!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longer than 63")
                .hasMessageNotContaining(pack);
    }

    static Stream<Arguments> kebabVerdicts() {
        // The grammar's own verdicts are IdentifierGrammarTest's; these say which grammar each type delegates to.
        return Stream.of(
                Arguments.of("a-b", true, true),
                Arguments.of("1a", true, false),
                Arguments.of("a--b", false, false),
                Arguments.of("Ab", false, false),
                Arguments.of("a-", false, false),
                Arguments.of("", false, false),
                Arguments.of(" ", false, false));
    }

    @ParameterizedTest(name = "\"{0}\": digit-first kinds {1}, letter-first kinds {2}")
    @MethodSource("kebabVerdicts")
    @DisplayName("short kebab ids keep their verdict")
    void shortKebabIdsKeepTheirVerdict(String id, boolean digitFirstValid, boolean letterFirstValid) {
        assertVerdict(GuardIdentifier::new, id, digitFirstValid);
        assertVerdict(ProcedureIdentifier::new, id, digitFirstValid);
        assertVerdict(ToolguideIdentifier::new, id, letterFirstValid);
        assertVerdict(SkillIdentifier::new, id, letterFirstValid);
        assertVerdict(GlossaryContextIdentifier::new, id, letterFirstValid);
        assertVerdict(TermIdentifier::new, id, letterFirstValid);
        if (!id.isEmpty()) {
            assertVerdict(IdentifierBoundsTest::newPreset, id, letterFirstValid);
        }
    }

    static Stream<Arguments> grammars() {
        // One row per grammar an identifier type delegates to (ADR-0075 decision 2): a valid text, the cap it is
        // valid at, and the shapes the old hand-rolled grammars accepted that the shared rules refuse.
        return Stream.of(
                Arguments.of(
                        "pack id: slug(63)", (Predicate<String>) PackIdentifier::isPackId, "1-pack", "p".repeat(63)),
                Arguments.of(
                        "digit-first slug(64)",
                        (Predicate<String>) IdentifierGrammar::isSlug,
                        "1-guard",
                        "g".repeat(64)),
                Arguments.of(
                        "letter-first slug(64)",
                        (Predicate<String>) IdentifierGrammar::isLetterFirstSlug,
                        "a-paradigm",
                        "s".repeat(64)),
                Arguments.of(
                        "role: letter-first slug(64)",
                        (Predicate<String>) RoleBearer::isRole,
                        "implementer",
                        "r".repeat(64)),
                Arguments.of(
                        "slot id: letter-first slug(64) on each side of the colon",
                        (Predicate<String>) text -> SlotId.tryParse(text).isPresent(),
                        "spdd:reasons-block",
                        "n".repeat(64) + ":" + "m".repeat(64)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("grammars")
    @DisplayName("each shared grammar accepts its canonical shape at its cap and refuses a doubled or trailing hyphen")
    void eachGrammarRefusesADoubledOrTrailingHyphen(
            String grammar, Predicate<String> accepts, String valid, String atCap) {
        assertThat(accepts.test(valid)).as(valid).isTrue();
        assertThat(accepts.test(atCap)).as("at the cap").isTrue();
        assertThat(accepts.test(atCap + "x")).as("one over the cap").isFalse();
        for (String refused : List.of("a--b", "a-", "-a", "A", "a_b", "")) {
            String text = grammar.startsWith("slot") ? "ns:" + refused : refused;
            assertThat(accepts.test(text)).as(text).isFalse();
        }
    }

    static Stream<Arguments> typesOnTheSharedGrammar() {
        // One smoke row per type: it delegates to its grammar, so a doubled hyphen is refused where it was accepted.
        return Stream.of(
                Arguments.of("ParadigmIdentifier", (Consumer<String>) ParadigmIdentifier::new, "a-b", "a--b"),
                Arguments.of("StyleguideIdentifier", (Consumer<String>) StyleguideIdentifier::new, "a-b", "a--b"),
                Arguments.of("TacticIdentifier", (Consumer<String>) TacticIdentifier::new, "a-b", "a--b"),
                Arguments.of("GuardIdentifier", (Consumer<String>) GuardIdentifier::new, "1-b", "1--b"),
                Arguments.of("ProcedureIdentifier", (Consumer<String>) ProcedureIdentifier::new, "1-b", "1--b"),
                Arguments.of("ToolguideIdentifier", (Consumer<String>) ToolguideIdentifier::new, "a-b", "a--b"),
                Arguments.of("SkillIdentifier", (Consumer<String>) SkillIdentifier::new, "a-b", "a--b"),
                Arguments.of(
                        "GlossaryContextIdentifier", (Consumer<String>) GlossaryContextIdentifier::new, "a-b", "a--b"),
                Arguments.of("TermIdentifier", (Consumer<String>) TermIdentifier::new, "a-b", "a--b"),
                Arguments.of("Preset id", (Consumer<String>) IdentifierBoundsTest::newPreset, "a-b", "a--b"),
                Arguments.of(
                        "ProcedureStep role",
                        (Consumer<String>) IdentifierBoundsTest::newProcedureStepRole,
                        "a-b",
                        "a--b"),
                Arguments.of("RolePins role", (Consumer<String>) IdentifierBoundsTest::newRolePin, "a-b", "a--b"),
                Arguments.of(
                        "StepEnhancement slot",
                        (Consumer<String>) IdentifierBoundsTest::newStepEnhancementSlot,
                        "spdd:reasons-block",
                        "spdd:reasons--block"),
                Arguments.of("SlotId", (Consumer<String>) SlotId::new, "spdd:reasons-block", "spdd-:reasons"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("typesOnTheSharedGrammar")
    @DisplayName("each identifier type accepts its grammar's shape and refuses a doubled or trailing hyphen")
    void eachTypeIsOnTheSharedGrammar(String type, Consumer<String> construct, String valid, String doubled) {
        assertThatCode(() -> construct.accept(valid)).as(valid).doesNotThrowAnyException();
        assertThatThrownBy(() -> construct.accept(doubled))
                .as(doubled)
                .isInstanceOfAny(ConstraintBreachException.class, IllegalArgumentException.class);
        assertThatThrownBy(() -> construct.accept(valid + "-"))
                .as(valid + "-")
                .isInstanceOfAny(ConstraintBreachException.class, IllegalArgumentException.class);
    }

    private static void assertVerdict(Consumer<String> construct, String text, boolean valid) {
        if (valid) {
            assertThatCode(() -> construct.accept(text)).as(text).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> construct.accept(text)).as(text).isInstanceOf(ConstraintBreachException.class);
        }
    }
}
