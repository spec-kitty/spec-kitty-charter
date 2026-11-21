package ai.speckitty.charter.packvalidation.roundtrip;

import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The whole-pack round trip's rule (ADR-0036; ADR-0075 decision 8, as amended by operator ruling 2 of 2026-11-15): over
 * the flow's result, every file of a modelled kind means the same after the round trip, every file the census does not
 * hold stays unwritten, and no written file is left unpaired, unless the expectation pins it.
 *
 * <p>The expectation decides what is pinned. The gate passes the checked-in baseline scoped to one example pack; the
 * drift test passes {@link WholePackExpectation#none()}, so every red is a problem. Either way every census file the
 * census holds needs an outcome: a held file of a kind the round trip does not model fails, naming its kind and path
 * (#547). A file the census does not hold (an excluded file, a file no rule covers) is left out, never red (#631).
 */
public final class WholePackRoundTripRule implements Rule<PackRoundTripResult> {

    /** The run differs from its expectation: a red file, a stale entry, an unmodelled kind (the detail says which). */
    public static final ReasonCode RED = new ReasonCode("round_trip_red", Severity.VIOLATION);

    /** A written file no source file pairs with (ADR-0036: an extra file is a difference). */
    public static final ReasonCode UNPAIRED = new ReasonCode("round_trip_written_unpaired", Severity.VIOLATION);

    private static final String RULE = "whole-pack round trip (ADR-0036)";

    /** The check its reasons come from: the rule itself. */
    private static final Origin ORIGIN = new Origin(RULE);

    private final WholePackExpectation expectation;

    /**
     * A rule that holds a round trip to an expectation.
     *
     * @param expectation what the run may show; {@link WholePackExpectation#none()} for none
     */
    public WholePackRoundTripRule(WholePackExpectation expectation) {
        this.expectation = Objects.requireNonNull(expectation, "expectation");
    }

    /**
     * The judged run, for a report: the per-file observation, its evaluation against the expectation, and the written
     * files left unpaired.
     *
     * @param observation the per-file details
     * @param evaluation the run judged against the expectation
     * @param unpaired the written files no source file pairs with, beyond the written copies of files the expectation
     *     lists as refused
     */
    public record Verdict(
            RoundTripObservation observation, WholePackExpectation.Evaluation evaluation, List<String> unpaired) {

        /**
         * Copies the list.
         *
         * @param observation the observation
         * @param evaluation the evaluation
         * @param unpaired the unpaired written files
         */
        public Verdict {
            Objects.requireNonNull(observation, "observation");
            Objects.requireNonNull(evaluation, "evaluation");
            unpaired = List.copyOf(unpaired);
        }

        /**
         * Every reason the run fails, the evaluation's problems first. A problem about one census file
         * ({@code UNEXPECTED_RED <path> (<kind>): ...}, as {@link WholePackExpectation} words it) is placed at that
         * file, so a surface that reports violations by file (pack-check, #478) points at it; a problem about the run
         * as a whole (a stale entry, an unmodelled schema) stays unplaced.
         *
         * @return the failing reasons; empty when the run passes
         */
        public List<Reason> reasons() {
            Set<String> census = evaluation.files().stream()
                    .map(WholePackExpectation.FileResult::path)
                    .collect(Collectors.toSet());
            List<Reason> reasons = new ArrayList<>();
            evaluation
                    .problems()
                    .forEach(problem -> reasons.add(
                            new Reason(RULE, ORIGIN, Evaluation.FAIL, RED, locationOf(problem, census), problem)));
            unpaired.forEach(file -> reasons.add(new Reason(
                    RULE, ORIGIN, Evaluation.FAIL, UNPAIRED, Location.in(file), "written but unpaired " + file)));
            return List.copyOf(reasons);
        }
    }

    /**
     * Where a problem of the evaluation lies: the census file its second word names, else nowhere.
     *
     * @param problem the problem, {@code <TOKEN> <subject> ...}
     * @param census the census files' paths
     * @return the file's location, or {@link Location#NOWHERE}
     */
    static Location locationOf(String problem, Set<String> census) {
        String[] words = problem.split(" ", 3);
        return words.length > 1 && census.contains(words[1]) ? Location.in(words[1]) : Location.NOWHERE;
    }

    /**
     * Judges one round trip.
     *
     * @param result what the flow observed
     * @return the verdict
     */
    public Verdict judge(PackRoundTripResult result) {
        RoundTripObservation observation = RoundTripObservation.of(result);
        WholePackExpectation.Evaluation evaluation =
                expectation.evaluate(result.census(), result.notHeld(), result.modelledKinds(), observation.observed());
        // The written copy of a file the expectation lists as refused may stay unpaired: its source never paired.
        Set<String> refusedAsExpected = evaluation.files().stream()
                .filter(file -> file.status() == WholePackExpectation.Status.EXPECTED_RED)
                .filter(file -> WholePackExpectation.atOf(file.detail()).equals("refused"))
                .map(WholePackExpectation.FileResult::path)
                .collect(Collectors.toSet());
        List<String> unpaired = observation.unpaired().stream()
                .filter(file -> !refusedAsExpected.contains(file))
                .toList();
        return new Verdict(observation, evaluation, unpaired);
    }

    /**
     * The reason of a round trip the flow refused before it could observe the pack (#478, M2): the pack cannot be
     * round-tripped, which is {@link #RED}, worded with the refusal's code and detail.
     *
     * @param code the refusal's wire token
     * @param detail the refusal's detail
     * @return the failing reason
     */
    public static Reason refused(String code, String detail) {
        return new Reason(
                RULE,
                ORIGIN,
                Evaluation.FAIL,
                RED,
                Location.NOWHERE,
                "the round trip refused the pack: " + code + ": " + detail);
    }

    @Override
    public List<Reason> apply(PackRoundTripResult result) {
        return judge(result).reasons();
    }
}
