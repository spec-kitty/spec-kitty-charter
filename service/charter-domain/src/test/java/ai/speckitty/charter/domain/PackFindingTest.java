package ai.speckitty.charter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PackFindingTest {

    private static final Set<PackFindingCode> LOAD_PATH_BLOCKING = EnumSet.of(
            PackFindingCode.PACK_LEVEL_MISMATCH,
            PackFindingCode.PACK_UNREADABLE,
            PackFindingCode.REQUIRES_CORE_INVALID,
            PackFindingCode.ORG_FRAGMENT_MISSING,
            PackFindingCode.DRG_SCHEMA_ERROR);

    private static PackFinding finding(PackFindingCode code) {
        return new PackFinding(code, "pack.yaml", List.of("a", "b"), "the detail");
    }

    @Test
    void onlyTheLevelMissingAndUnknownKeyCodesAreWarnings() {
        assertThat(EnumSet.allOf(PackFindingCode.class).stream()
                        .filter(c -> c.severity() == PackFindingCode.Severity.WARNING))
                .containsExactlyInAnyOrder(PackFindingCode.PACK_LEVEL_MISSING, PackFindingCode.PACK_UNKNOWN_KEY);
    }

    @ParameterizedTest
    @EnumSource(PackFindingCode.class)
    void aFindingTakesItsSeverityFromItsCode(PackFindingCode code) {
        PackFindingCode.Severity expected =
                code == PackFindingCode.PACK_LEVEL_MISSING || code == PackFindingCode.PACK_UNKNOWN_KEY
                        ? PackFindingCode.Severity.WARNING
                        : PackFindingCode.Severity.BLOCKING;
        assertThat(finding(code).severity()).isEqualTo(expected);
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
    void theUnknownKeyWarningProjectsOntoItsDiagnosticAndHasNoRefusal() {
        assertThat(finding(PackFindingCode.PACK_UNKNOWN_KEY).toDiagnostic())
                .isEqualTo(new Diagnostic(Diagnostic.Code.PACK_UNKNOWN_KEY, "the detail"));
        assertThatThrownBy(PackFindingCode.PACK_UNKNOWN_KEY::refusal).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theOtherLoadPathBlockingCodesProjectOntoTheirRefusals() {
        assertThat(finding(PackFindingCode.PACK_UNREADABLE).code().refusal()).isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(finding(PackFindingCode.REQUIRES_CORE_INVALID).code().refusal())
                .isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(finding(PackFindingCode.ORG_FRAGMENT_MISSING).code().refusal())
                .isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(finding(PackFindingCode.DRG_SCHEMA_ERROR).code().refusal()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR);
    }

    @ParameterizedTest
    @EnumSource(PackFindingCode.class)
    void aValidateOnlyCodeHasNoWireValue(PackFindingCode code) {
        if (LOAD_PATH_BLOCKING.contains(code)
                || code == PackFindingCode.PACK_LEVEL_MISSING
                || code == PackFindingCode.PACK_UNKNOWN_KEY) {
            return;
        }
        assertThatThrownBy(code::refusal).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(code::diagnostic).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(finding(code)::toRefusal).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(finding(code)::toDiagnostic).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aWarningHasNoRefusalAndABlockingCodeHasNoDiagnostic() {
        assertThatThrownBy(PackFindingCode.PACK_LEVEL_MISSING::refusal).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(PackFindingCode.PACK_LEVEL_MISMATCH::diagnostic).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void thePlaceIsCopied() {
        java.util.ArrayList<String> place = new java.util.ArrayList<>(List.of("x"));
        PackFinding f = new PackFinding(PackFindingCode.DRG_SCHEMA_ERROR, "a.graph.yaml", place, "d");
        place.add("y");
        assertThat(f.place()).containsExactly("x");
    }
}
