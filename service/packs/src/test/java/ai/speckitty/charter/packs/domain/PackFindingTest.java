package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PackFindingTest {

    private static final Set<ReasonCode> LOAD_PATH_BLOCKING = Set.of(
            PackFindingCode.PACK_LEVEL_MISMATCH,
            PackFindingCode.PACK_UNREADABLE,
            PackFindingCode.REQUIRES_CORE_INVALID,
            PackFindingCode.PACK_VERSION_INVALID,
            PackFindingCode.DEPENDENCY_RANGE_INVALID,
            PackFindingCode.ORG_FRAGMENT_MISSING,
            PackCodes.DRG_SCHEMA_ERROR,
            PackCodes.PROMPT_FRAGMENT_INVALID,
            PackCodes.PROMPT_SLOT_MALFORMED,
            PackCodes.PROMPT_SLOT_NOT_EMPTY);

    private static final Set<ReasonCode> LOAD_PATH_WARNINGS = Set.of(
            PackFindingCode.PACK_LEVEL_MISSING, PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING, PackFindingCode.FILE_NOT_HELD);

    /** Every code a finding is built with: the domain's codes, then the staging view's. */
    static Stream<ReasonCode> codes() {
        return Stream.concat(
                PackCodes.CONTENT.stream(),
                Stream.of(
                        PackFindingCode.PACK_LEVEL_MISSING,
                        PackFindingCode.PACK_LEVEL_MISMATCH,
                        PackFindingCode.ORG_FRAGMENT_MISSING,
                        PackFindingCode.PACK_UNREADABLE,
                        PackFindingCode.REQUIRES_CORE_INVALID,
                        PackFindingCode.PACK_VERSION_INVALID,
                        PackFindingCode.DEPENDENCY_RANGE_INVALID,
                        PackFindingCode.ARTIFACT_SCHEMA_ERROR,
                        PackFindingCode.UNREADABLE,
                        PackFindingCode.ASSET_MIME_INVALID,
                        PackFindingCode.FILE_NOT_HELD));
    }

    private static PackFinding finding(ReasonCode code) {
        return new PackFinding(code, "pack.yaml", List.of("a", "b"), "the detail");
    }

    @Test
    void onlyTheWarningCodesAreWarnings() {
        assertThat(codes().filter(c -> c.severity() == Severity.WARNING))
                .containsExactlyInAnyOrderElementsOf(LOAD_PATH_WARNINGS);
    }

    @ParameterizedTest
    @MethodSource("codes")
    void aFindingTakesItsSeverityFromItsCode(ReasonCode code) {
        assertThat(finding(code).severity()).isEqualTo(code.severity());
    }

    @Test
    void theLevelFindingsProjectOntoTheirPinnedWireValues() {
        assertThat(finding(PackFindingCode.PACK_LEVEL_MISSING).toDiagnostic())
                .isEqualTo(new Diagnostic(Diagnostic.Code.PACK_LEVEL_MISSING, "the detail"));
        CharterRefusal refusal = finding(PackFindingCode.PACK_LEVEL_MISMATCH).toRefusal();
        assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_LEVEL_MISMATCH);
        assertThat(refusal.getMessage()).isEqualTo("the detail");
    }

    @Test
    void theFileNotHeldWarningProjectsOntoItsDiagnosticAndNoRefusal() {
        assertThat(finding(PackFindingCode.FILE_NOT_HELD).toDiagnostic())
                .isEqualTo(new Diagnostic(Diagnostic.Code.FILE_NOT_HELD, "the detail"));
        assertThatThrownBy(() -> PackFindingCode.refusal(PackFindingCode.FILE_NOT_HELD))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theSlotWarningProjectsOntoTheSlotDiagnosticAndTheViolationOntoItsRefusal() {
        assertThat(finding(PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING).toDiagnostic())
                .isEqualTo(new Diagnostic(Diagnostic.Code.PROMPT_SLOT_NOT_EMPTY, "the detail"));
        assertThat(PackFindingCode.refusal(PackCodes.PROMPT_SLOT_NOT_EMPTY))
                .isEqualTo(RefusalCode.PROMPT_SLOT_NOT_EMPTY);
        assertThat(PackFindingCode.diagnostic(PackCodes.PROMPT_SLOT_NOT_EMPTY)).isEmpty();
    }

    @Test
    void theOtherLoadPathBlockingCodesProjectOntoTheirRefusals() {
        assertThat(PackFindingCode.refusal(PackFindingCode.PACK_UNREADABLE)).isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(PackFindingCode.refusal(PackFindingCode.REQUIRES_CORE_INVALID))
                .isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(PackFindingCode.refusal(PackFindingCode.DEPENDENCY_RANGE_INVALID))
                .isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(PackFindingCode.refusal(PackFindingCode.ORG_FRAGMENT_MISSING))
                .isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(PackFindingCode.refusal(PackCodes.DRG_SCHEMA_ERROR)).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR);
    }

    @ParameterizedTest
    @MethodSource("codes")
    void aValidateOnlyCodeHasNoWireValue(ReasonCode code) {
        if (LOAD_PATH_BLOCKING.contains(code) || LOAD_PATH_WARNINGS.contains(code)) {
            return;
        }
        assertThatThrownBy(() -> PackFindingCode.refusal(code)).isInstanceOf(IllegalStateException.class);
        assertThat(PackFindingCode.diagnostic(code)).isEmpty();
        assertThatThrownBy(finding(code)::toRefusal).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(finding(code)::toDiagnostic).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aWarningHasNoRefusalAndABlockingCodeHasNoDiagnostic() {
        assertThatThrownBy(() -> PackFindingCode.refusal(PackFindingCode.PACK_LEVEL_MISSING))
                .isInstanceOf(IllegalStateException.class);
        assertThat(PackFindingCode.diagnostic(PackFindingCode.PACK_LEVEL_MISMATCH))
                .isEmpty();
    }

    @Test
    void thePlaceIsCopied() {
        List<String> place = new ArrayList<>(List.of("x"));
        PackFinding f = new PackFinding(PackCodes.DRG_SCHEMA_ERROR, "a.graph.yaml", place, "d");
        place.add("y");
        assertThat(f.place()).containsExactly("x");
    }
}
