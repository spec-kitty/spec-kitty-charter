package ai.speckitty.charter.packvalidation.placement;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import java.util.List;

/**
 * An org pack is its fragment (R16; #554, S2, R2 row 37): when the pack is at the org level and
 * {@code drg/fragment.yaml} is absent, it has no graph to contribute, {@link PlacementCodes#ORG_FRAGMENT_MISSING}.
 * The level is the configured one, or for a bare path the one its descriptor declares. An unreadable fragment is not
 * a missing one: its own finding speaks.
 */
public final class OrgFragmentRules {

    /** Where an org pack keeps its graph. */
    static final String FRAGMENT = "drg/fragment.yaml";

    /** Every org fragment rule. */
    public static final Rule<Placement> RULES = OrgFragmentRules::fragmentMissing;

    private OrgFragmentRules() {}

    static List<Reason> fragmentMissing(Placement placement) {
        PackContents contents = placement.read().contents();
        if (!(contents.fragment() instanceof FileRead.Absent<RawGraph>) || !isOrgPack(placement)) {
            return List.of();
        }
        return List.of(LevelRules.finding(
                PlacementCodes.ORG_FRAGMENT_MISSING, "org pack " + placement.name() + " has no " + FRAGMENT));
    }

    static boolean isOrgPack(Placement placement) {
        if (placement.configuredLevel().isPresent()) {
            return placement.configuredLevel().get() == PackLevel.ORG;
        }
        return placement.read().contents().descriptor() instanceof FileRead.Read<PackDescriptor> read
                && read.value()
                        .declaredLevel()
                        .filter(level -> level == PackLevel.ORG)
                        .isPresent();
    }
}
