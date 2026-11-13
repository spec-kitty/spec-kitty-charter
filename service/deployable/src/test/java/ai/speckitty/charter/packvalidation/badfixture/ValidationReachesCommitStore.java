package ai.speckitty.charter.packvalidation.badfixture;

import ai.speckitty.charter.packs.infra.storage.CommitStorePackWriteStore;
import org.jspecify.annotations.Nullable;

/** A pack-validation class that names the packs slice's commit store: a check reads packs, it never writes them. */
public final class ValidationReachesCommitStore {

    private ValidationReachesCommitStore() {}

    /** Names the store the rule must refuse. */
    public static @Nullable CommitStorePackWriteStore store() {
        return null;
    }
}
