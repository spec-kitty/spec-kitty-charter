package ai.speckitty.charter.packvalidation.badfixture;

import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;

/**
 * A pack-validation class that names the storage rules' package: the known-bad control of
 * {@code STORAGE_RULES_STAY_IN_THE_STORAGE_EDGE}. The validation edge reaches the storage verdict through the domain
 * port {@code PackReading} only (#554, S6, C-003).
 */
public final class ValidationNamesTheStorageRules {

    private ValidationNamesTheStorageRules() {}

    /**
     * Answers a storage edge's token by hand.
     *
     * @return the token of an unreadable file
     */
    public static ReasonCode unreadable() {
        return StorageCodes.UNREADABLE;
    }
}
