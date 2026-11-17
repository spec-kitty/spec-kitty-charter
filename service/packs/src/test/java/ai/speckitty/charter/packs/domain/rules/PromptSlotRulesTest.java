package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackContents.PromptSlotFacts;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import ai.speckitty.charter.packs.domain.rules.PlantedFixtures.Expected;
import ai.speckitty.charter.packs.domain.rules.PlantedFixtures.Planted;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("PromptSlotRules (#554, T037)")
class PromptSlotRulesTest {

    private static final String PLAN = "missions/software-dev/steps/plan.prompt.md";

    private static final String REVIEW = "missions/software-dev/steps/review.prompt.md";

    private static final String UNPAIRED = "# Plan\n\n<!-- spdd:reasons-block:start -->\n";

    private static final String FILLED =
            "# Plan\n\n<!-- spdd:reasons-block:start -->legacy<!-- spdd:reasons-block:end -->\n";

    private static final String EMPTY = "# Plan\n\n<!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->\n";

    private static FileRead<PackDescriptor> declaring(Optional<String> schemaVersion) {
        return new FileRead.Read<>(
                "pack.yaml",
                new PackDescriptor(
                        "01ARWG13C00000000000001ABC",
                        "1.0.0",
                        Optional.empty(),
                        "qa",
                        Optional.of(PackLevel.ORG),
                        Optional.empty(),
                        PackPaths.none(),
                        List.of(),
                        List.of(),
                        schemaVersion));
    }

    private static PackContents prompts(FileRead<PackDescriptor> descriptor, PromptSlotFacts... prompts) {
        return new PackContents(
                new PackRoot("qa", Optional.of(PackLevel.ORG), "loc"),
                descriptor,
                List.of(),
                new FileRead.Absent<>("drg/fragment.yaml"),
                List.of(),
                List.of(),
                StepEnhancementFacts.none(),
                List.of(prompts),
                List.of());
    }

    private static PromptSlotFacts prompt(String file, String text) {
        return new PromptSlotFacts(file, PromptSlots.parse(text));
    }

    private static final FileRead<PackDescriptor> VERSIONED = declaring(Optional.of("1.0"));

    private static final FileRead<PackDescriptor> UNVERSIONED = declaring(Optional.empty());

    @Test
    @DisplayName("a malformed marker is one prompt_slot_malformed at the prompt, naming its line, whatever the version")
    void aMalformedMarkerIsAViolationWhateverTheVersion() {
        for (FileRead<PackDescriptor> descriptor : List.of(VERSIONED, UNVERSIONED)) {
            assertThat(PromptSlotRules.SLOTS.apply(prompts(descriptor, prompt(PLAN, UNPAIRED))))
                    .singleElement()
                    .satisfies(finding -> {
                        assertThat(finding.code()).isEqualTo(PackCodes.PROMPT_SLOT_MALFORMED);
                        assertThat(finding.severity()).isEqualTo(Severity.VIOLATION);
                        assertThat(finding.isFailing()).isTrue();
                        assertThat(finding.location()).isEqualTo(Location.in(PLAN));
                        assertThat(finding.detail()).startsWith(PLAN + ": line 3 of the canonical text: ");
                    });
        }
        assertThat(PromptSlotRules.SLOTS.apply(prompts(VERSIONED, prompt(PLAN, EMPTY))))
                .isEmpty();
    }

    @Test
    @DisplayName(
            "a slot with text is the violation under schema_version 1.0, and the warning of the same token without")
    void aSlotWithTextFollowsTheVersionGate() {
        Reason violation = PromptSlotRules.SLOTS
                .apply(prompts(VERSIONED, prompt(PLAN, FILLED)))
                .getFirst();
        Reason warning = PromptSlotRules.SLOTS
                .apply(prompts(UNVERSIONED, prompt(PLAN, FILLED)))
                .getFirst();

        assertThat(violation.code()).isEqualTo(PackCodes.PROMPT_SLOT_NOT_EMPTY);
        assertThat(violation.detail())
                .isEqualTo(PLAN + ": line 3 of the canonical text: slot spdd:reasons-block holds text");
        assertThat(warning.code()).isEqualTo(PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING);
        assertThat(warning.severity()).isEqualTo(Severity.WARNING);
        assertThat(warning.code().value()).isEqualTo(violation.code().value());
        assertThat(warning.detail())
                .isEqualTo(violation.detail() + "; the descriptor declares no schema_version, so this is a warning");
    }

    @Test
    @DisplayName("only schema_version 1.0 is versioned; another version, or a descriptor the read refused, warns")
    void onlyVersionOneZeroIsVersioned() {
        FileRead<PackDescriptor> refused =
                new FileRead.Unreadable<>("pack.yaml", new ParseFailure("pack.yaml", "not YAML"), List.of());

        assertThat(PromptSlotRules.SLOTS.apply(prompts(declaring(Optional.of("0.9")), prompt(PLAN, FILLED))))
                .extracting(Reason::code)
                .containsExactly(PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING);
        assertThat(PromptSlotRules.SLOTS.apply(prompts(refused, prompt(PLAN, FILLED))))
                .extracting(Reason::code)
                .containsExactly(PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING);
    }

    @Test
    @DisplayName(
            "the faults follow the read's order of the prompts, then the parse's, malformed and filled interleaved")
    void theFaultsFollowTheReadsOrder() {
        PackContents contents =
                prompts(VERSIONED, prompt(REVIEW, FILLED + "\n<!-- spdd:x:start -->\n"), prompt(PLAN, UNPAIRED));

        assertThat(PromptSlotRules.RULES.apply(contents))
                .extracting(reason -> reason.location().file(), Reason::code)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(REVIEW, PackCodes.PROMPT_SLOT_NOT_EMPTY),
                        org.assertj.core.groups.Tuple.tuple(REVIEW, PackCodes.PROMPT_SLOT_MALFORMED),
                        org.assertj.core.groups.Tuple.tuple(PLAN, PackCodes.PROMPT_SLOT_MALFORMED));
    }

    /** The tokens of the rule's three codes; the slot's violation and its warning share one token. */
    private static final Set<String> TOKENS = Set.copyOf(List.of(
            PackCodes.PROMPT_SLOT_MALFORMED.value(),
            PackCodes.PROMPT_SLOT_NOT_EMPTY.value(),
            PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING.value()));

    /**
     * Every plant with an {@code expected.json}. None plants a prompt slot fault today (a new plant would add golden
     * rows), so the oracle pins that the rule finds nothing the plants do not expect; the hand-written facts above pin
     * each code's finding.
     */
    static List<Planted> withExpectations() {
        return PlantedFixtures.all().stream()
                .filter(planted -> planted.expected().isPresent())
                .toList();
    }

    @ParameterizedTest
    @MethodSource("withExpectations")
    @DisplayName("a plant's expected.json is the oracle of the rule's findings")
    void thePlantsExpectedFindingsAreTheOracle(Planted planted) {
        assertThat(PromptSlotRules.RULES.apply(planted.contents()))
                .extracting(Expected::of)
                .containsExactlyElementsOf(planted.expected().orElseThrow().stream()
                        .filter(expected -> TOKENS.contains(expected.code()))
                        .toList());
    }
}
