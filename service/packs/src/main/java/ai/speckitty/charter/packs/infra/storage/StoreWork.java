package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.metering.WorkUnit;

/** The work the pack store does, as a {@link ai.speckitty.charter.kernel.metering.WorkMeter} counts it. */
public enum StoreWork implements WorkUnit {
    /** One whole read of a pack ({@link FilesystemPackStore#read} and its overloads). */
    WHOLE_READ,
    /** One file parsed by a lookup by id. */
    LOOKUP_PARSE,
    /** One walk of a pack tree ({@link PackFileSet#walk}). */
    TREE_WALK,
    /** One run of the store half of a read ({@code StoreRead}). */
    STORE_RUN
}
