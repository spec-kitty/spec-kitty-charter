package ai.speckitty.charter.deployable.bdd;

import ai.speckitty.charter.deployable.bdd.ContractConformance.Success;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Stub: the coverage check is not implemented yet. */
public final class OperationCoverage {

    private OperationCoverage() {}

    static Optional<Success> exercised(ContractConformance contract, String method, String path, int status) {
        return Optional.empty();
    }

    static List<String> gaps(Set<Success> declared, Set<Success> exercised) {
        return List.of();
    }
}
