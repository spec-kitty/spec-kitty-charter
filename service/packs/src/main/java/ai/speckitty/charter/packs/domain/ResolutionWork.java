package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.metering.WorkUnit;

/** The work an in-force resolution does, as a {@link ai.speckitty.charter.kernel.metering.WorkMeter} counts it. */
public enum ResolutionWork implements WorkUnit {
    /** One in-force resolution. */
    RESOLUTION,
    /** One node visited by a walk of a resolution's override graph. */
    RESOLUTION_VISIT
}
