package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * An {@link AvailablePacks} that answers one snapshot a test sets, or one refusal, and counts the reads, so a test pins
 * that a use case reads the packs exactly as often as NFR-007 allows. It records the roots of each read. A session's
 * walks answer from a graph a test sets ({@link #walks}); every walk is recorded, so a test sees what the use case
 * asked. {@link #merged} and {@link #resolves} set what a session answers for the merged closure and the in-force
 * resolution; each asked question is counted, so a test sees that the whole request answered from the one read.
 */
public final class FakeAvailablePacks implements AvailablePacks {

    private ClosureSnapshot snapshot;
    private @Nullable CharterRefusal refusal;
    private final List<Optional<List<String>>> calls = new ArrayList<>();
    private Function<ReachPlan, Reach> graph = plan -> Reach.empty();
    private final List<ReachPlan> walked = new ArrayList<>();
    private MergedSnapshot merged = MergedSnapshot.empty();
    private Function<InForceQuery, InForceAnswer> resolution = query -> InForceAnswer.empty();
    private int merges;
    private final List<InForceQuery> queries = new ArrayList<>();

    /**
     * A fake that answers the snapshot.
     *
     * @param snapshot the answer of every call
     */
    public FakeAvailablePacks(ClosureSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * Answers another snapshot from now on.
     *
     * @param next the answer
     */
    public void answer(ClosureSnapshot next) {
        this.snapshot = next;
        this.refusal = null;
    }

    /**
     * Refuses every call from now on, as the compose would.
     *
     * @param next the refusal
     */
    public void refuse(CharterRefusal next) {
        this.refusal = next;
    }

    /**
     * Answers every walk of a session with this graph from now on.
     *
     * @param next the walks' answers, by plan
     */
    public void walks(Function<ReachPlan, Reach> next) {
        this.graph = next;
    }

    /**
     * Answers this merged closure to every session from now on.
     *
     * @param next the answer of {@link PacksSession#merged}
     */
    public void merged(MergedSnapshot next) {
        this.merged = next;
    }

    /**
     * Answers every in-force question of a session with this resolution from now on.
     *
     * @param next the answers of {@link PacksSession#inForce}, by query
     */
    public void resolves(Function<InForceQuery, InForceAnswer> next) {
        this.resolution = next;
    }

    @Override
    public PacksSession session(Optional<List<String>> packIds) {
        calls.add(packIds);
        if (refusal != null) {
            throw refusal;
        }
        return new Session(snapshot);
    }

    /**
     * How often the packs were read.
     *
     * @return the number of {@link #session} calls, a {@link #snapshot} included
     */
    public int calls() {
        return calls.size();
    }

    /**
     * The roots of the last call.
     *
     * @return the roots; empty for every configured pack
     */
    public Optional<List<String>> lastRoots() {
        return calls.getLast();
    }

    /**
     * How often the sessions were asked for the merged closure.
     *
     * @return the number of {@link PacksSession#merged} calls
     */
    public int merges() {
        return merges;
    }

    /**
     * The in-force questions the sessions were asked, in order.
     *
     * @return the queries
     */
    public List<InForceQuery> queries() {
        return List.copyOf(queries);
    }

    /**
     * The plans the sessions walked, in order.
     *
     * @return the plans
     */
    public List<ReachPlan> walked() {
        return List.copyOf(walked);
    }

    /** A session over the snapshot it was opened with and the fake's graph. */
    private final class Session implements PacksSession {

        private final ClosureSnapshot opened;

        Session(ClosureSnapshot opened) {
            this.opened = opened;
        }

        @Override
        public ClosureSnapshot snapshot() {
            return opened;
        }

        @Override
        public Reach reach(ReachPlan plan) {
            walked.add(plan);
            return graph.apply(plan);
        }

        @Override
        public MergedSnapshot merged() {
            merges++;
            return merged;
        }

        @Override
        public InForceAnswer inForce(InForceQuery query) {
            queries.add(query);
            return resolution.apply(query);
        }
    }
}
