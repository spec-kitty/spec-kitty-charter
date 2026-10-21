package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.Diagnostic;
import java.util.List;

/**
 * The governance the offering says applies to one action of one mission type.
 *
 * @param action the action's URN
 * @param missionType the mission type's URN
 * @param depth how many {@code suggests} hops the walk followed; at least 0, with no upper bound in the answer
 * @param governanceFingerprint the digest of the governance the answer was computed from
 *     ({@link ai.speckitty.charter.packs.domain.CharterOffering#governanceFingerprint()})
 * @param sourceFingerprint the digest of the input files the answer was computed from, verified by
 *     {@link ConsistentLoad}; the inbound adapter reports it as a header, never in the body
 * @param guidance the artifacts the walk lists, sorted by URN; empty when nothing is in scope
 * @param diagnostics the load findings of the offering, the same as the pack listing reports per pack, in load order
 */
public record ActionContext(
        Urn action,
        Urn missionType,
        int depth,
        String governanceFingerprint,
        String sourceFingerprint,
        List<GuidanceEntry> guidance,
        List<Diagnostic> diagnostics) {
    /**
     * Copies {@code guidance} and {@code diagnostics}.
     *
     * @param action the action's URN
     * @param missionType the mission type's URN
     * @param depth the depth of the walk
     * @param governanceFingerprint the digest of the governance
     * @param sourceFingerprint the digest of the input files
     * @param guidance the artifacts the walk lists
     * @param diagnostics the load findings of the offering
     */
    public ActionContext {
        guidance = List.copyOf(guidance);
        diagnostics = List.copyOf(diagnostics);
    }
}
