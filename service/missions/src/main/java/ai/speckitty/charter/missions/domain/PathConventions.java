package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Where a mission of the type keeps its work: directories in the mission's workspace, not pack files. At least one is
 * named; a type that names none has no path conventions.
 *
 * @param workspace the working directory, when named
 * @param tests the tests directory, when named
 * @param deliverables the deliverables directory, when named
 * @param documentation the documentation directory, when named
 * @param data the data directory, when named
 */
public record PathConventions(
        Optional<String> workspace,
        Optional<String> tests,
        Optional<String> deliverables,
        Optional<String> documentation,
        Optional<String> data) {

    private static final ModularRuleset<List<String>> RULES = ModularRuleset.<List<String>>emptyRules()
            .must(Constraints.of("Path conventions name at least one directory.", named -> !named.isEmpty()))
            .must(Constraints.of(
                    "A path convention is not blank.", named -> named.stream().noneMatch(String::isBlank)));

    /**
     * Checks that at least one directory is named and none is blank.
     *
     * @param workspace the working directory, possibly empty
     * @param tests the tests directory, possibly empty
     * @param deliverables the deliverables directory, possibly empty
     * @param documentation the documentation directory, possibly empty
     * @param data the data directory, possibly empty
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when none is named or one is blank
     */
    public PathConventions {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(tests, "tests");
        Objects.requireNonNull(deliverables, "deliverables");
        Objects.requireNonNull(documentation, "documentation");
        Objects.requireNonNull(data, "data");
        RULES.guard(Stream.of(workspace, tests, deliverables, documentation, data)
                .flatMap(Optional::stream)
                .toList());
    }
}
