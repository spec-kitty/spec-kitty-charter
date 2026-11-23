package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one mapping of a pack's verdict (#478, S3): the refusal of a failing verdict and the diagnostics of a passing one,
 * in the domain, so registration and pack-doctor answer a pack in the same words.
 */
@DisplayName("PackRefusalMapping: a pack's verdict as its refusal or its diagnostics (#478, S3)")
class PackRefusalMappingTest {

    private static final ReasonCode SCHEMA = new ReasonCode("artifact_schema_error", Severity.VIOLATION);

    private static final ReasonCode NOT_HELD = new ReasonCode("file_not_held", Severity.WARNING);

    private static Reason failing(ReasonCode code, String file, String detail) {
        return new Reason(code.value(), Origin.NONE, Evaluation.FAIL, code, Location.in(file), detail);
    }

    @Test
    @DisplayName(
            "a failing verdict is validation_failed: one violation per violation, in order, and the problem detail")
    void aFailingVerdictIsTheRefusal() {
        Rationale verdict = new Rationale(List.of(
                failing(SCHEMA, "tactics/b.tactic.yaml", "tactics/b.tactic.yaml: 'id' is missing"),
                Reason.passed("checked"),
                failing(NOT_HELD, "notes.txt", "notes.txt: not held (no pack file kind models this file)"),
                failing(PackCodes.DUPLICATE_ARTIFACT_ID, "tactics/a.tactic.yaml", "already declared")));

        CharterRefusal refusal = PackRefusalMapping.of(verdict);

        assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(refusal.getMessage())
                .isEqualTo("The pack cannot be registered: 2 fault(s), the first at tactics/b.tactic.yaml.");
        assertThat(refusal.violations())
                .containsExactly(
                        new RefusalViolation(
                                "tactics/b.tactic.yaml",
                                "artifact_schema_error",
                                "tactics/b.tactic.yaml: 'id' is missing"),
                        new RefusalViolation(
                                "tactics/a.tactic.yaml",
                                "duplicate_artifact_id",
                                "tactics/a.tactic.yaml: already declared"));
    }

    @Test
    @DisplayName("the violation mapping is the refusal's violations, and a passing verdict has none")
    void theViolationsAreTheRefusals() {
        Rationale verdict = new Rationale(List.of(
                failing(PackCodes.DUPLICATE_ARTIFACT_ID, "tactics/a.tactic.yaml", "already declared"),
                failing(NOT_HELD, "notes.txt", "notes.txt: not held (no pack file kind models this file)")));

        assertThat(PackRefusalMapping.violations(verdict))
                .containsExactly(new RefusalViolation(
                        "tactics/a.tactic.yaml", "duplicate_artifact_id", "tactics/a.tactic.yaml: already declared"))
                .isEqualTo(PackRefusalMapping.of(verdict).violations());
        assertThat(PackRefusalMapping.violations(new Rationale(List.of(Reason.passed("checked")))))
                .isEmpty();
    }

    @Test
    @DisplayName(
            "a pack's unreadable graph documents are pack_unreadable: the detail names the first document and its reason,"
                    + " and the violations are registration's (#617, #628)")
    void unreadableGraphDocumentsAreThePackUnreadableRefusal() {
        Rationale faults = new Rationale(List.of(
                failing(PackCodes.DRG_SCHEMA_ERROR, "directive.graph.yaml", "edges[0].relation: unknown relation"),
                failing(PackCodes.DRG_SCHEMA_ERROR, "drg/fragment.yaml", "edge endpoint 'local-t' is not a URN")));

        CharterRefusal refusal = PackRefusalMapping.graphUnreadable(new PackIdentifier("foundation"), faults);

        assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(refusal.getMessage())
                .isEqualTo("a graph document of foundation cannot be read: foundation/directive.graph.yaml:"
                        + " edges[0].relation: unknown relation");
        assertThat(refusal.violations())
                .containsExactly(
                        new RefusalViolation(
                                "directive.graph.yaml",
                                "drg_schema_error",
                                "directive.graph.yaml: edges[0].relation: unknown relation"),
                        new RefusalViolation(
                                "drg/fragment.yaml",
                                "drg_schema_error",
                                "drg/fragment.yaml: edge endpoint 'local-t' is not a URN"));
    }

    @Test
    @DisplayName("graph faults with no violation refuse nothing")
    void noGraphFaultRefusesNothing() {
        Rationale none = new Rationale(List.of(Reason.passed("checked")));

        assertThatThrownBy(() -> PackRefusalMapping.graphUnreadable(new PackIdentifier("foundation"), none))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a passing verdict refuses nothing")
    void aPassingVerdictRefusesNothing() {
        Rationale verdict = new Rationale(List.of(Reason.passed("checked")));

        assertThatThrownBy(() -> PackRefusalMapping.of(verdict))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a verdict with no violation refuses nothing");
    }

    @Test
    @DisplayName("a passing verdict's warnings are the diagnostics, in order, by the token they spell")
    void aPassingVerdictsWarningsAreTheDiagnostics() {
        Rationale verdict = new Rationale(List.of(
                failing(PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING, "p.prompt.md", "p.prompt.md: slot x holds text"),
                failing(NOT_HELD, "notes.txt", "not held (no pack file kind models this file)"),
                Reason.passed("checked")));

        assertThat(PackRefusalMapping.diagnostics(verdict))
                .containsExactly(
                        new Diagnostic(Diagnostic.Code.PROMPT_SLOT_NOT_EMPTY, "p.prompt.md: slot x holds text"),
                        new Diagnostic(
                                Diagnostic.Code.FILE_NOT_HELD,
                                "notes.txt: not held (no pack file kind models this file)"));
    }

    @Test
    @DisplayName("a warning no diagnostic code spells is a programming error, not a silent drop")
    void anUnspelledWarningIsAnError() {
        Rationale verdict =
                new Rationale(List.of(failing(new ReasonCode("mystery", Severity.WARNING), "a.yaml", "a.yaml: what")));

        assertThatThrownBy(() -> PackRefusalMapping.diagnostics(verdict))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mystery is a warning no diagnostic code spells");
    }
}
