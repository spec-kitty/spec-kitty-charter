package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.Optional;

/** The port through which a pack's procedures are fetched one at a time, whatever the storage. Procedures have no PUT; a removal goes through the generic removal. */
public interface ProcedureRepository {

    /**
     * Fetches one procedure.
     *
     * @param pack the pack that owns the procedure
     * @param procedure the procedure's identity
     * @return the procedure, or empty when the pack has none with that identity
     */
    Optional<Procedure> load(PackIdentifier pack, ProcedureIdentifier procedure);
}
