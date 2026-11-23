package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PackCodes: the domain's codes, each token built once (#554, R2, S3)")
class PackCodesTest {

    /** The placement tokens: built once in pack-validation's PlacementCodes (WP09), not here and not in storage. */
    private static final Set<String> PLACEMENT =
            Set.of("pack_level_missing", "pack_level_mismatch", "org_fragment_missing");

    /**
     * Every constant of the two tables, each once: {@code StorageCodes} names two of {@code PackCodes}' constants
     * ({@code pack_unreadable} and {@code validation_failed}, #556), which is one constant named twice, not a token
     * built twice.
     */
    private static List<ReasonCode> bothTables() {
        Set<ReasonCode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ReasonCode> both = new ArrayList<>();
        for (List<ReasonCode> table : List.of(
                PackCodes.CONTENT,
                PackCodes.CLOSURE,
                PackCodes.closureChecks().stream().map(PackCodes::closure).toList(),
                StorageCodes.ORDERED)) {
            table.stream().filter(seen::add).forEach(both::add);
        }
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
                        RefusalCode.PACK_SIBLING_CONFLICT.token(),
                        RefusalCode.PRESET_ITEM_NOT_HELD.token(),
                        RefusalCode.PRESET_ITEM_UNDECLARED.token());
        assertThat(PackCodes.CLOSURE)
                .allSatisfy(code -> assertThat(code.severity()).isEqualTo(Severity.VIOLATION));
    }

    @Test
    @DisplayName("ALL holds the content, CLOSURE and closure-check codes and nothing else")
    void allHoldsTheContentAndClosureCodes() {
        List<ReasonCode> checks =
                PackCodes.closureChecks().stream().map(PackCodes::closure).toList();
        Set<ReasonCode> expected = new HashSet<>(PackCodes.CONTENT);
        expected.addAll(PackCodes.CLOSURE);
        expected.addAll(checks);

        assertThat(PackCodes.ALL).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("every closure-check code is a violation spelt as its refusal code, and maps back to it (#556)")
    void everyClosureCheckCodeIsItsRefusalCodesToken() {
        assertThat(PackCodes.closureChecks()).isNotEmpty().allSatisfy(code -> {
            ReasonCode reason = PackCodes.closure(code);
            assertThat(reason.value()).isEqualTo(code.token());
            assertThat(reason.severity()).isEqualTo(Severity.VIOLATION);
            assertThat(PackCodes.refusalOf(reason)).isEqualTo(code);
        });
        assertThat(PackCodes.closureChecks())
                .containsAll(List.of(
                        RefusalCode.PACK_UNREADABLE,
                        RefusalCode.VALIDATION_FAILED,
                        RefusalCode.PACK_REFERENCE_UNQUALIFIED,
                        RefusalCode.SELF_OVERRIDE,
                        RefusalCode.FALLBACK_PROFILE_NOT_AGENT,
                        RefusalCode.GOVERNANCE_POLICY_VIOLATION,
                        RefusalCode.PACK_HAS_DEPENDENTS));
        assertThat(StorageCodes.PACK_UNREADABLE).isSameAs(PackCodes.PACK_UNREADABLE);
        assertThat(StorageCodes.ASSEMBLY_REFUSED).isSameAs(PackCodes.VALIDATION_FAILED);
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
