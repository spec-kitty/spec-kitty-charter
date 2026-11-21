package ai.speckitty.charter.packs.domain.roundtrip;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One pack's whole-pack round trip (ADR-0036): every file of the pack with its gate kind, what happened to each file of
 * a modelled kind, and every file the write left in the target. A census file of a kind the round trip does not model
 * has no outcome; the verdict judges it: a held file of such a kind fails (#547). A file the census does not hold
 * ({@link #notHeld}) is left out: the round trip is never handed it, so it has no outcome, and the verdict checks only
 * that the write did not produce it (operator ruling R34, #631).
 *
 * @param census every pack-relative path ({@code /}-separated) to its gate kind: a {@code file-kinds.yaml} schema name, or
 *     {@link #OTHER}, {@link #RETIRED}, {@link #EXCLUDED} or {@link #SYMLINK}
 * @param notHeld every census path the census does not hold ({@code TreeCensus.notHeld}): an excluded file, a file no
 *     rule covers, and a mapped file nothing names (a {@code policy.yaml} the descriptor does not name, a companion no
 *     artifact names)
 * @param modelledKinds the gate kinds the round trip models
 * @param outcomes one outcome per source path of a modelled kind that the round trip answered
 * @param written every regular file under the target, target-relative and {@code /}-separated
 */
public record PackRoundTripResult(
        SortedMap<String, String> census,
        SortedSet<String> notHeld,
        Set<String> modelledKinds,
        SortedMap<String, FileOutcome> outcomes,
        SortedSet<String> written) {

    /** The gate kind of a symbolic link: counted, never followed, so "every file once" holds. */
    public static final String SYMLINK = "other:symlink";

    /** The prefix of a gate kind for a file no schema covers, followed by its first path segment. */
    public static final String OTHER = "other:";

    /** The prefix of a retired file's gate kind, followed by its rule's glob (ADR-0044 decision 6). */
    public static final String RETIRED = "retired:";

    /**
     * The prefix of an excluded file's gate kind, followed by the glob of the rule that excludes it (#451): the verdict
     * checks that the write did not produce it.
     */
    public static final String EXCLUDED = "excluded:";

    /**
     * Copies every collection, unmodifiable.
     *
     * @param census the census
     * @param notHeld the census paths not held
     * @param modelledKinds the modelled kinds
     * @param outcomes the outcomes
     * @param written the written files
     */
    public PackRoundTripResult {
        census = Collections.unmodifiableSortedMap(new TreeMap<>(Objects.requireNonNull(census, "census")));
        notHeld = Collections.unmodifiableSortedSet(new TreeSet<>(Objects.requireNonNull(notHeld, "notHeld")));
        if (!census.keySet().containsAll(notHeld)) {
            throw new IllegalArgumentException("a path not held is not in the census: " + notHeld);
        }
        modelledKinds = Set.copyOf(modelledKinds);
        outcomes = Collections.unmodifiableSortedMap(new TreeMap<>(Objects.requireNonNull(outcomes, "outcomes")));
        written = Collections.unmodifiableSortedSet(new TreeSet<>(Objects.requireNonNull(written, "written")));
    }

    /**
     * Whether a census kind names an excluded file.
     *
     * @param kind a census kind
     * @return whether it starts with {@link #EXCLUDED}
     */
    public static boolean isExcluded(String kind) {
        return kind.startsWith(EXCLUDED);
    }
}
