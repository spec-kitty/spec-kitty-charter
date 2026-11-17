package ai.speckitty.charter.packvalidation.placement;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.Diagnostic;
import java.util.Set;

/**
 * The placement tokens (#554, S2, squad-shaping Q7): built here, the only module that emits them. A placement rule
 * judges where a pack is deployed, which is validate mode's own extension; registration never answers these codes.
 * Each code owns its severity (ruling 4). Where a contract enum already spells a token
 * ({@link Diagnostic.Code#PACK_LEVEL_MISSING}, {@link RefusalCode#PACK_LEVEL_MISMATCH}), the constant is built from that
 * spelling, so the token has one source; only this class and the enums name those two constants
 * (HexagonalArchitectureTest, rules A and B).
 */
public final class PlacementCodes {

    /** The pack has no {@code pack.yaml}, or its descriptor declares no {@code pack_level}: a warning (R2 row 35). */
    public static final ReasonCode PACK_LEVEL_MISSING =
            new ReasonCode(Diagnostic.Code.PACK_LEVEL_MISSING.token(), Severity.WARNING);

    /** The pack declares a level other than the one it is configured at (R2 row 36): the wire refusal's token. */
    public static final ReasonCode PACK_LEVEL_MISMATCH =
            new ReasonCode(RefusalCode.PACK_LEVEL_MISMATCH.token(), Severity.VIOLATION);

    /** An org pack has no {@code drg/fragment.yaml} (R2 row 37). */
    public static final ReasonCode ORG_FRAGMENT_MISSING = new ReasonCode("org_fragment_missing", Severity.VIOLATION);

    /** Every placement code: the group the verdict's precedence tests first ({@code Precedence.comparator}). */
    public static final Set<ReasonCode> ALL = Set.of(PACK_LEVEL_MISSING, PACK_LEVEL_MISMATCH, ORG_FRAGMENT_MISSING);

    private PlacementCodes() {}
}
