package ai.speckitty.charter.missions.domain.badfixture;

import ai.speckitty.charter.packs.domain.Pack;

/** A missions-domain class that names the packs slice: the known-bad of its purity and slice-isolation rules. */
public final class MissionDomainReachesPacks {

    private MissionDomainReachesPacks() {}

    /**
     * Names a packs type, as a missions domain must not.
     *
     * @param pack a pack
     * @return the pack's text
     */
    public static String describe(Pack pack) {
        return pack.toString();
    }
}
