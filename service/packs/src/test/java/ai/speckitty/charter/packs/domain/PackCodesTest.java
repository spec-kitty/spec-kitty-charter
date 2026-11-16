package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PackCodes: the domain's codes, each token built once (#554, R2, S3)")
class PackCodesTest {

    /** The placement tokens: built once in pack-validation's PlacementCodes (WP09), not here and not in storage. */
    private static final Set<String> PLACEMENT =
            Set.of("pack_level_missing", "pack_level_mismatch", "org_fragment_missing");

    private static List<ReasonCode> bothTables() {
        List<ReasonCode> both = new ArrayList<>(PackCodes.CONTENT);
        both.addAll(PackCodes.CLOSURE);
        both.addAll(StorageCodes.ORDERED);
        return both;
    }

    @Test
    @DisplayName("every token is snake case")
    void everyTokenIsSnakeCase() {
        assertThat(bothTables()).allSatisfy(code -> assertThat(code.value()).matches("[a-z][a-z0-9_]*"));
    }

    @Test
    @DisplayName("no two constants of the two tables share a token and a severity")
    void noTwoConstantsShareATokenAndASeverity() {
        List<ReasonCode> both = bothTables();

        assertThat(new HashSet<>(both)).hasSameSizeAs(both);
    }

    @Test
    @DisplayName("the slot that holds text is one token at two severities, two constants")
    void theSlotThatHoldsTextIsOneTokenAtTwoSeverities() {
        assertThat(PackCodes.PROMPT_SLOT_NOT_EMPTY.value()).isEqualTo(PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING.value());
        assertThat(PackCodes.PROMPT_SLOT_NOT_EMPTY.severity()).isEqualTo(Severity.VIOLATION);
        assertThat(PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING.severity()).isEqualTo(Severity.WARNING);
        assertThat(PackCodes.PROMPT_SLOT_NOT_EMPTY).isNotEqualTo(PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING);
    }

    @Test
    @DisplayName("every diagnostic code has a constant in one table, apart from the placement tokens")
    void everyDiagnosticCodeHasAConstant() {
        Set<String> warnings = new HashSet<>();
        bothTables().stream()
                .filter(code -> code.severity() == Severity.WARNING)
                .forEach(code -> warnings.add(code.value()));

        assertThat(Arrays.stream(Diagnostic.Code.values())
                        .map(Diagnostic.Code::token)
                        .filter(token -> !PLACEMENT.contains(token)))
                .isNotEmpty()
                .allSatisfy(token -> assertThat(warnings).contains(token));
    }

    @Test
    @DisplayName("no table builds a placement token")
    void noTableBuildsAPlacementToken() {
        assertThat(bothTables()).noneSatisfy(code -> assertThat(PLACEMENT).contains(code.value()));
    }

    @Test
    @DisplayName("a token the contract enum spells is the contract's token (A-m1)")
    void aTokenTheContractSpellsIsTheContractsToken() {
        assertThat(PackCodes.DRG_SCHEMA_ERROR.value()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR.token());
        assertThat(PackCodes.PROMPT_FRAGMENT_INVALID.value()).isEqualTo(RefusalCode.PROMPT_FRAGMENT_INVALID.token());
        assertThat(PackCodes.CLOSURE)
                .extracting(ReasonCode::value)
                .containsExactly(
                        RefusalCode.PACK_DEPENDENCY_CYCLE.token(),
                        RefusalCode.PACK_DEPENDENCY_UNKNOWN.token(),
                        RefusalCode.PACK_DEPENDENCY_OUT_OF_RANGE.token(),
                        RefusalCode.PACK_PARENT_UNRESOLVED.token(),
                        RefusalCode.PACK_LEVEL_VIOLATION.token(),
                        RefusalCode.PACK_NAME_CONFLICT.token(),
                        RefusalCode.PACK_DUPLICATE_ID.token(),
                        RefusalCode.PACK_SIBLING_CONFLICT.token());
        assertThat(PackCodes.CLOSURE)
                .allSatisfy(code -> assertThat(code.severity()).isEqualTo(Severity.VIOLATION));
    }

    @Test
    @DisplayName("ALL holds the content and CLOSURE codes and nothing else")
    void allHoldsTheContentAndClosureCodes() {
        assertThat(PackCodes.ALL).hasSize(PackCodes.CONTENT.size() + PackCodes.CLOSURE.size());
        assertThat(PackCodes.ALL).containsAll(PackCodes.CONTENT).containsAll(PackCodes.CLOSURE);
    }

    @Test
    @DisplayName("the one wording puts the file before the reason, once")
    void theOneWordingPutsTheFileBeforeTheReasonOnce() {
        assertThat(PackCodes.detail("tactics/a.tactic.yaml", "while parsing a flow node"))
                .isEqualTo("tactics/a.tactic.yaml: while parsing a flow node");
        assertThat(PackCodes.detail("pack.yaml", "pack.yaml of qa: unknown key 'mystery'"))
                .isEqualTo("pack.yaml of qa: unknown key 'mystery'");
        assertThat(PackCodes.detail("", "the pack root cannot be read")).isEqualTo("the pack root cannot be read");
    }
}
