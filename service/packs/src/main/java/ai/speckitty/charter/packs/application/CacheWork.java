package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.metering.WorkUnit;

/** The work of the composition facts cache, as a {@link ai.speckitty.charter.kernel.metering.WorkMeter} counts it. */
public enum CacheWork implements WorkUnit {
    /** One pack's facts read from a store. */
    LOAD,
    /** One pack's facts answered from an entry. */
    HIT
}
