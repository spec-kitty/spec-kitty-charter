package ai.speckitty.charter.packcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packcheck.Problems.Problem;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackVerdict;
import ai.speckitty.charter.packs.domain.rules.CompositionRules.CompositionFault;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packvalidation.roundtrip.WholePackRoundTripRule;
import ai.speckitty.charter.packvalidation.set.PackSetCheck;
import ai.speckitty.charter.packvalidation.set.PackSetEntry;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.Check;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.PackResult;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link Problems}: each branch of the rendering, the cap of 100 violations with its left-out sentence, the titles and
 * the status table (#478, T016). Each verdict is a real pack's result, read by the one reader, with the reasons of the
 * branch put in its place.
 */
class ProblemsTest {

    @TempDir
    Path temp;

    private PackResult real;

    @BeforeEach
    void readAnExamplePack() {
        Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp);
        PackSetVerdict verdict = new PackSetCheck(new FileSystemPackInspector())
                .check(List.of(
                        new PackSetEntry(new PackRoot(ExamplePacks.BUILT_IN, Optional.empty(), builtIn.toString()))));
        real = verdict.packs().getFirst();
    }

    private static Reason violation(String code, String file, String detail) {
        return new Reason("test", Evaluation.FAIL, new ReasonCode(code, Severity.VIOLATION), Location.in(file), detail);
    }

    private PackResult with(List<Reason> findings, PackReadout.Facts facts, List<Reason> roundTrip, List<Reason> set) {
        return new PackResult(real.entry(), new PackVerdict(List.of(), findings), real.level(), facts, roundTrip, set);
    }

    private static PackSetVerdict verdict(List<PackResult> packs, List<CompositionFault> faults) {
        return new PackSetVerdict(packs, faults, List.of(Check.PACK), List.of());
    }

    private String instance() {
        return real.entry().root().locator();
    }

    @Test
    void aValidPackHasNoProblem() {
        assertThat(Problems.of(verdict(List.of(real), List.of()))).isEmpty();
    }

    @Test
    void aPackWithViolationsIsOneValidationFailedProblemInTheRegistrationsWords() {
        PackResult pack = with(
                List.of(
                        violation(
                                "duplicate_artifact_id", "tactics/b.tactic.yaml", "tactic id 'a' is already declared"),
                        violation("drg_schema_error", "drg/fragment.yaml", "drg/fragment.yaml: not a mapping")),
                real.facts(),
                List.of(),
                List.of());

        assertThat(Problems.of(verdict(List.of(pack), List.of())))
                .singleElement()
                .isEqualTo(new Problem(
                        "Validation failed",
                        422,
                        "The pack cannot be registered: 2 fault(s), the first at tactics/b.tactic.yaml.",
                        Optional.of(instance()),
                        "validation_failed",
                        List.of(
                                new RefusalViolation(
                                        "tactics/b.tactic.yaml",
                                        "duplicate_artifact_id",
                                        "tactics/b.tactic.yaml: tactic id 'a' is already declared"),
                                new RefusalViolation(
                                        "drg/fragment.yaml", "drg_schema_error", "drg/fragment.yaml: not a mapping")),
                        List.of()));
    }

    @Test
    void aWarningIsNoProblem() {
        Reason warning = new Reason(
                "test",
                Evaluation.FAIL,
                new ReasonCode("file_not_held", Severity.WARNING),
                Location.in("notes.md"),
                "no schema models it");

        assertThat(Problems.of(verdict(List.of(with(List.of(warning), real.facts(), List.of(), List.of())), List.of())))
                .isEmpty();
    }

    @Test
    void theSetChecksLevelFindingJoinsThePacksProblem() {
        PackResult pack = with(
                List.of(), real.facts(), List.of(), List.of(violation("pack_level_missing", "pack.yaml", "no level")));

        assertThat(Problems.of(verdict(List.of(pack), List.of())))
                .singleElement()
                .satisfies(problem -> {
                    assertThat(problem.code()).isEqualTo("validation_failed");
                    assertThat(problem.violations())
                            .extracting(RefusalViolation::code)
                            .containsExactly("pack_level_missing");
                });
    }

    @Test
    void anUnreadablePackWithNoViolationIsItsRefusal() {
        PackResult pack = with(
                List.of(),
                new PackReadout.Unreadable(RefusalCode.PACK_UNREADABLE, "a graph cannot be read"),
                List.of(),
                List.of());

        assertThat(Problems.of(verdict(List.of(pack), List.of())))
                .singleElement()
                .isEqualTo(new Problem(
                        "Pack unreadable",
                        500,
                        "a graph cannot be read",
                        Optional.of(instance()),
                        "pack_unreadable",
                        List.of(),
                        List.of()));
    }

    @Test
    void anUnreadablePackWithAViolationHasOneProblemNotTwo() {
        PackResult pack = with(
                List.of(violation("drg_schema_error", "drg/fragment.yaml", "not a mapping")),
                new PackReadout.Unreadable(RefusalCode.PACK_UNREADABLE, "a graph cannot be read"),
                List.of(),
                List.of());

        assertThat(Problems.of(verdict(List.of(pack), List.of())))
                .extracting(Problem::code)
                .containsExactly("validation_failed");
    }

    @Test
    void theRoundTripsReasonsAreTheirOwnValidationFailedProblemAfterThePacks() {
        PackResult pack = with(
                List.of(violation("duplicate_artifact_id", "tactics/b.tactic.yaml", "repeated")),
                real.facts(),
                List.of(WholePackRoundTripRule.refused("pack_unreadable", "pack.yaml cannot be read")),
                List.of());

        List<Problem> problems = Problems.of(verdict(List.of(pack), List.of()));

        assertThat(problems).extracting(Problem::code).containsExactly("validation_failed", "validation_failed");
        assertThat(problems.get(0).violations())
                .extracting(RefusalViolation::code)
                .containsExactly("duplicate_artifact_id");
        assertThat(problems.get(1).violations())
                .extracting(RefusalViolation::code)
                .containsExactly("round_trip_red");
        assertThat(problems.get(1).instance()).contains(instance());
    }

    @Test
    void aCompositionFaultIsItsCodeWithItsPacksAndNoInstance() {
        RefusalPack project = new RefusalPack("01M74H0000000000000000PRJ1", "project", "project");
        CompositionFault fault = new CompositionFault(
                PackCodes.CLOSURE.get(1), List.of(project), "project depends on a pack the set does not hold");

        assertThat(Problems.of(verdict(List.of(real), List.of(fault))))
                .singleElement()
                .isEqualTo(new Problem(
                        "Pack dependency unknown",
                        422,
                        "project depends on a pack the set does not hold",
                        Optional.empty(),
                        "pack_dependency_unknown",
                        List.of(),
                        List.of(project)));
    }

    @Test
    void thePacksProblemsComeBeforeTheCompositionProblems() {
        PackResult pack = with(
                List.of(violation("duplicate_artifact_id", "a.yaml", "repeated")), real.facts(), List.of(), List.of());
        CompositionFault fault =
                new CompositionFault(PackCodes.CLOSURE.getFirst(), List.of(new RefusalPack("x", "y", "z")), "a cycle");

        assertThat(Problems.of(verdict(List.of(pack), List.of(fault))))
                .extracting(Problem::code)
                .containsExactly("validation_failed", "pack_dependency_cycle");
    }

    @Test
    void aHundredViolationsAreAllListed() {
        Problem problem =
                Problems.of(verdict(List.of(withViolations(100)), List.of())).getFirst();

        assertThat(problem.violations()).hasSize(100);
        assertThat(problem.detail()).isEqualTo("The pack cannot be registered: 100 fault(s), the first at f0.yaml.");
    }

    @Test
    void aHundredAndOneViolationsListTheFirstHundredAndCountTheRest() {
        Problem problem =
                Problems.of(verdict(List.of(withViolations(101)), List.of())).getFirst();

        assertThat(problem.violations()).hasSize(100);
        assertThat(problem.violations().getLast().pointer()).isEqualTo("f99.yaml");
        assertThat(problem.detail())
                .isEqualTo(
                        "The pack cannot be registered: 101 fault(s), the first at f0.yaml. 1 more violations are not"
                                + " listed.");
    }

    private PackResult withViolations(int count) {
        return with(
                IntStream.range(0, count)
                        .mapToObj(n -> violation("duplicate_artifact_id", "f" + n + ".yaml", "repeated"))
                        .toList(),
                real.facts(),
                List.of(),
                List.of());
    }

    @Test
    void theLeftOutSentenceIsEmptyUpToTheCap() {
        assertThat(Problems.leftOut(0)).isEmpty();
        assertThat(Problems.leftOut(100)).isEmpty();
        assertThat(Problems.leftOut(102)).isEqualTo(" 2 more violations are not listed.");
    }

    @Test
    void aTitleIsTheCodeAsASentence() {
        assertThat(Problems.title(RefusalCode.VALIDATION_FAILED)).isEqualTo("Validation failed");
        assertThat(Problems.title(RefusalCode.PACK_DEPENDENCY_OUT_OF_RANGE)).isEqualTo("Pack dependency out of range");
        assertThat(Problems.title(RefusalCode.DRG_SCHEMA_ERROR)).isEqualTo("Invalid DRG document");
    }

    @Test
    void everyClosureCodeHasTheRegistrationsStatus() {
        assertThat(PackCodes.CLOSURE)
                .allSatisfy(code ->
                        assertThat(Problems.statusOf(refusalCode(code.value()))).isEqualTo(422));
        assertThat(Problems.statusOf(RefusalCode.VALIDATION_FAILED)).isEqualTo(422);
        assertThat(Problems.statusOf(RefusalCode.PACK_UNREADABLE)).isEqualTo(500);
        assertThat(Problems.statusOf(RefusalCode.REGISTRATION_CONFLICT)).isEqualTo(409);
    }

    private static RefusalCode refusalCode(String token) {
        return Arrays.stream(RefusalCode.values())
                .filter(code -> code.token().equals(token))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aCodeOutsideTheTableIsRefusedLoudly() {
        assertThatThrownBy(() -> Problems.statusOf(RefusalCode.INTERNAL_ERROR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("internal_error has no status in pack-check's table");
    }
}
