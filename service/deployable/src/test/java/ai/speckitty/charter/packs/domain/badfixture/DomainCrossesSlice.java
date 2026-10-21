package ai.speckitty.charter.packs.domain.badfixture;

import ai.speckitty.charter.profiles.domain.badfixture.ForeignSliceType;

/** A packs-domain class that names a type of another slice: the known-bad of the slice-isolation rule. */
public final class DomainCrossesSlice {

    /** A foreign slice type held by the domain. */
    @SuppressWarnings("unused")
    private ForeignSliceType foreign;
}
