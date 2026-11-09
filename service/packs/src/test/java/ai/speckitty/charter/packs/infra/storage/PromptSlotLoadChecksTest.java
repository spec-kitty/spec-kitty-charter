package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackPolicy.PromptSlotFault;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A step prompt's slots are judged at load, in validate mode and at registration (FR-007, IC-03): a malformed marker is
 * blocking whatever the descriptor's version, a slot that holds text is blocking under {@code schema_version "1.0"} and
 * a warning, listed apart from the violations, for an unversioned descriptor (A4). Each case sits beside its twin.
 */
@DisplayName("Step prompt slots are judged behind the descriptor-version gate (IC-03)")
class PromptSlotLoadChecksTest {

    private static final Optional<String> UNVERSIONED = Optional.empty();
    private static final Optional<String> VERSIONED = Optional.of("1.0");
    private static final String FILE = "missions/software-dev/steps/plan.prompt.md";

    private static final String UNPAIRED = "# Plan\n\n<!-- spdd:reasons-block:start -->\n";
    private static final String FILLED =
            "# Plan\n\n<!-- spdd:reasons-block:start -->legacy<!-- spdd:reasons-block:end -->\n";
    private static final String EMPTY = "# Plan\n\n<!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->\n";

    private static List<PromptSlotFault> faults(String text, Optional<String> version) {
        return PackPolicy.promptSlotFaults(PromptSlots.parse(text), version);
    }

    // ---- the gate, as the policy decides it (T023) ----------------------------------------------------------------

    @Test
    @DisplayName("a malformed marker is prompt_slot_malformed whatever the version, beside the empty slot that is none")
    void aMalformedMarkerIsBlockingWhateverTheVersion() {
        assertThat(faults(UNPAIRED, UNVERSIONED))
                .singleElement()
                .satisfies(fault -> assertThat(fault.code()).isEqualTo(PackFindingCode.PROMPT_SLOT_MALFORMED));
        assertThat(faults(UNPAIRED, VERSIONED))
                .singleElement()
                .satisfies(fault -> assertThat(fault.code()).isEqualTo(PackFindingCode.PROMPT_SLOT_MALFORMED));
        assertThat(faults(EMPTY, UNVERSIONED)).isEmpty();
        assertThat(faults(EMPTY, VERSIONED)).isEmpty();
    }

    @Test
    @DisplayName("a slot with text is blocking under schema_version 1.0 and a warning for an unversioned descriptor")
    void aSlotWithTextFollowsTheVersionGate() {
        assertThat(faults(FILLED, VERSIONED))
                .singleElement()
                .satisfies(fault -> assertThat(fault.code()).isEqualTo(PackFindingCode.PROMPT_SLOT_NOT_EMPTY));
        assertThat(faults(FILLED, UNVERSIONED))
                .singleElement()
                .satisfies(
                        fault -> assertThat(fault.code()).isEqualTo(PackFindingCode.PROMPT_SLOT_NOT_EMPTY_UNVERSIONED));
    }

    @Test
    @DisplayName("any other declared version gates like an unversioned one: only 1.0 is the versioned contract")
    void onlyVersionOneZeroIsVersioned() {
        assertThat(faults(FILLED, Optional.of("0.9")))
                .singleElement()
                .satisfies(
                        fault -> assertThat(fault.code()).isEqualTo(PackFindingCode.PROMPT_SLOT_NOT_EMPTY_UNVERSIONED));
    }

    @Test
    @DisplayName("a fault names its marker's line, and the warning says why it is only a warning")
    void aFaultNamesItsLine() {
        assertThat(faults(UNPAIRED, VERSIONED).getFirst().reason()).startsWith("line 3: ");
        assertThat(faults(FILLED, VERSIONED).getFirst().reason())
                .startsWith("line 3: ")
                .contains("spdd:reasons-block");
        assertThat(faults(FILLED, UNVERSIONED).getFirst().reason())
                .contains("spdd:reasons-block")
                .contains("the descriptor declares no schema_version, so this is a warning");
    }

    @Test
    @DisplayName("a blocking fault is a violation of its wire token at the file; the warning is a finding only")
    void aBlockingFaultIsAViolationAndAWarningIsNot() {
        PromptSlotFault malformed = faults(UNPAIRED, UNVERSIONED).getFirst();
        PromptSlotFault notEmpty = faults(FILLED, VERSIONED).getFirst();
        PromptSlotFault legacy = faults(FILLED, UNVERSIONED).getFirst();

        assertThat(PackPolicy.violationOf(FILE, malformed))
                .isEqualTo(new PackElementViolation(FILE, "prompt_slot_malformed", malformed.reason()));
        assertThat(PackPolicy.violationOf(FILE, notEmpty).kind()).isEqualTo("prompt_slot_not_empty");
        assertThatThrownBy(() -> PackPolicy.violationOf(FILE, legacy)).isInstanceOf(IllegalArgumentException.class);

        PackFinding warning = PackPolicy.findingOf(FILE, legacy);
        assertThat(warning.severity()).isEqualTo(PackFindingCode.Severity.WARNING);
        assertThat(warning.file()).isEqualTo(FILE);
        assertThat(warning.detail()).startsWith(FILE + ": ").contains(legacy.reason());
        assertThat(PackPolicy.findingOf(FILE, notEmpty).severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
    }

    @Test
    @DisplayName("the codes project onto the refusal codes and the diagnostic code the contract declares")
    void theCodesProjectOntoTheWire() {
        assertThat(PackFindingCode.PROMPT_SLOT_MALFORMED.refusal()).isEqualTo(RefusalCode.PROMPT_SLOT_MALFORMED);
        assertThat(PackFindingCode.PROMPT_SLOT_NOT_EMPTY.refusal()).isEqualTo(RefusalCode.PROMPT_SLOT_NOT_EMPTY);
        assertThat(PackFindingCode.PROMPT_SLOT_NOT_EMPTY_UNVERSIONED.diagnostic())
                .isEqualTo(Diagnostic.Code.PROMPT_SLOT_NOT_EMPTY);
    }
}
