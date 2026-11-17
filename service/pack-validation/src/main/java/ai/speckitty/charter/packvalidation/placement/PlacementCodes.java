package ai.speckitty.charter.packvalidation.placement;

import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import java.util.Set;

/**
 * The placement tokens (#554, S2, squad-shaping Q7): built here, the only module that emits them. A placement rule
 * judges where a pack is deployed, which is validate mode's own extension; registration never answers these codes.
 * Each code owns its severity (ruling 4).
 */
public final class PlacementCodes {

    /** The pack has no {@code pack.yaml}, or its descriptor declares no {@code pack_level}: a warning (R2 row 35). */
    public static final ReasonCode PACK_LEVEL_MISSING = new ReasonCode("pack_level_missing", Severity.WARNING);

    /**
     * The pack declares a level other than the one it is configured at (R2 row 36). Its token is the wire refusal's,
     * written here: only the pack policy and the inbound edge name that refusal code (HexagonalArchitectureTest, rule B).
     */
    public static final ReasonCode PACK_LEVEL_MISMATCH = new ReasonCode("pack_level_mismatch", Severity.VIOLATION);

    /** An org pack has no {@code drg/fragment.yaml} (R2 row 37). */
    public static final ReasonCode ORG_FRAGMENT_MISSING = new ReasonCode("org_fragment_missing", Severity.VIOLATION);

    /** Every placement code: the group the verdict's precedence tests first ({@code Precedence.comparator}). */
    public static final Set<ReasonCode> ALL = Set.of(PACK_LEVEL_MISSING, PACK_LEVEL_MISMATCH, ORG_FRAGMENT_MISSING);

    private PlacementCodes() {}
}
