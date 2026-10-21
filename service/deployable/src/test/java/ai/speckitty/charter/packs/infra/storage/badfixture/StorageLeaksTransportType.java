package ai.speckitty.charter.packs.infra.storage.badfixture;

import ai.speckitty.charter.packs.domain.PackRepository;
import org.springframework.http.ResponseEntity;

/** A storage port implementation whose public method returns a non-domain type: the known-bad of that rule. */
public abstract class StorageLeaksTransportType implements PackRepository {

    /**
     * Returns a transport type.
     *
     * @return a response entity, which a storage adapter must never return
     */
    public abstract ResponseEntity<String> leak();
}
