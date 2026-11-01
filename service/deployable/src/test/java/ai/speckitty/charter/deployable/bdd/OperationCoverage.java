package ai.speckitty.charter.deployable.bdd;

import ai.speckitty.charter.deployable.bdd.ContractConformance.Success;
import io.cucumber.java.AfterAll;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The operation-coverage check of the contract suite (R-20): every success answer the contract declares, each 2xx
 * status of each operation, is exercised by at least one exchange of some scenario. Otherwise an operation no
 * scenario calls would escape the conformance and link checks. The scenario hook records each 2xx exchange by the
 * operation that answers its method and path; after the last scenario the check fails, listing the gaps.
 *
 * <p>It judges the whole suite, so a run that selects only some scenarios (a tag or name filter) reports the
 * operations the others exercise as gaps.
 */
public final class OperationCoverage {

    private static final Set<Success> EXERCISED = ConcurrentHashMap.newKeySet();

    private OperationCoverage() {}

    /**
     * Records one exchange of a scenario, if it is a success answer of an operation of the contract.
     *
     * @param contract the contract
     * @param method the lower-case method
     * @param path the path below the server prefix, without its query
     * @param status the response status
     */
    static void record(ContractConformance contract, String method, String path, int status) {
        exercised(contract, method, path, status).ifPresent(EXERCISED::add);
    }

    /**
     * The success answer an exchange exercises.
     *
     * @param contract the contract
     * @param method the lower-case method
     * @param path the path below the server prefix, without its query
     * @param status the response status
     * @return the operation's success at that status, or empty when the status is not 2xx or no operation defines the
     *     method on the path
     */
    static Optional<Success> exercised(ContractConformance contract, String method, String path, int status) {
        if (status < 200 || status > 299) {
            return Optional.empty();
        }
        return contract.operationTemplate(method, path).map(template -> new Success(method, template, status));
    }

    /**
     * The declared success answers that were not exercised.
     *
     * @param declared the success answers the contract declares
     * @param exercised the success answers the scenarios exercised
     * @return each gap as {@code METHOD template status}, sorted
     */
    static List<String> gaps(Set<Success> declared, Set<Success> exercised) {
        return declared.stream()
                .filter(success -> !exercised.contains(success))
                .sorted(Comparator.comparing(Success::template)
                        .thenComparing(Success::method)
                        .thenComparingInt(Success::status))
                .map(Success::toString)
                .toList();
    }

    /** Fails the suite when a success answer of the contract was exercised by no scenario. */
    @AfterAll
    public static void everySuccessAnswerIsExercised() {
        List<String> gaps = gaps(new ContractConformance().successResponses(), Set.copyOf(EXERCISED));
        if (!gaps.isEmpty()) {
            throw new AssertionError("No scenario exercises these success answers of the contract (R-20); add one for"
                    + " each:\n  " + String.join("\n  ", gaps));
        }
    }
}
