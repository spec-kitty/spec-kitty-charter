package ai.speckitty.charter.packvalidation;

import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.ClassifiedFile;
import ai.speckitty.charter.packs.domain.PackVerdict;
import ai.speckitty.charter.packs.domain.rules.Precedence;
import ai.speckitty.charter.packvalidation.placement.PlacementCodes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Validate mode's mapper (#554, FR-008): the findings of a rationale as the verdict's, in the plan's precedence
 * ({@link Precedence#comparator}) with the placement codes as the group it tests first, beside the classification of
 * every file. It reads a rationale's failures only, and names no storage code.
 */
public final class PackVerdicts {

    private static final Comparator<Reason> PRECEDENCE = Precedence.comparator(PlacementCodes.ALL);

    private PackVerdicts() {}

    /**
     * The verdict of a pack.
     *
     * @param files the classified files of the pack
     * @param all the findings: the readout's rationale and placement's, joined
     * @return the verdict, with every failing reason in precedence order
     */
    public static PackVerdict of(List<ClassifiedFile> files, Rationale all) {
        List<Reason> findings = new ArrayList<>(all.failures());
        findings.sort(PRECEDENCE);
        return new PackVerdict(files, findings);
    }
}
