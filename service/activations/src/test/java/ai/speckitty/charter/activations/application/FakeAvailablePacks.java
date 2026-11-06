package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * An {@link AvailablePacks} that answers one snapshot a test sets, or one refusal, and counts the reads, so a test pins
 * that a use case reads the packs exactly as often as NFR-007 allows. It records the roots of each read. A session's
 * walks answer from a graph a test sets ({@link #walks}), the {@code enhances} pull (bounded by the kinds it is asked
 * for, as the packs adapter bounds it) and the enhancers from the pairs a test sets ({@link #enhances}); every walk is recorded, so a test sees what the use case asked.
 */
public final class FakeAvailablePacks implements AvailablePacks {

    private ClosureSnapshot snapshot;
    private @Nullable CharterRefusal refusal;
    private final List<Optional<List<String>>> calls = new ArrayList<>();
    private Function<ReachPlan, Reach> graph = plan -> Reach.empty();
    private final Map<ArtifactRef, Set<ArtifactRef>> enhancers = new TreeMap<>();
    private final List<ReachPlan> walked = new ArrayList<>();

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
     * Records that an artifact enhances another, for the pull and the enhancers of a session.
     *
     * @param enhancer the enhancer
     * @param target the artifact it enhances
     */
    public void enhances(ArtifactRef enhancer, ArtifactRef target) {
        enhancers.computeIfAbsent(target, ignored -> new TreeSet<>()).add(enhancer);
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
        public Set<ArtifactRef> enhancesPull(Set<ArtifactRef> starts, Set<ArtifactKind> kinds) {
            Set<ArtifactRef> pulled = new TreeSet<>();
            List<ArtifactRef> frontier = new ArrayList<>(starts);
            while (!frontier.isEmpty()) {
                ArtifactRef at = frontier.removeLast();
                for (ArtifactRef next : neighbours(at)) {
                    if (kinds.contains(next.kind()) && !starts.contains(next) && pulled.add(next)) {
                        frontier.add(next);
                    }
                }
            }
            return pulled;
        }

        /** Both ways over enhances: the artifact's enhancers and the targets it enhances. */
        private Set<ArtifactRef> neighbours(ArtifactRef at) {
            Set<ArtifactRef> neighbours = new TreeSet<>(enhancers.getOrDefault(at, Set.of()));
            enhancers.forEach((target, by) -> {
                if (by.contains(at)) {
                    neighbours.add(target);
                }
            });
            return neighbours;
        }

        @Override
        public Map<ArtifactRef, Set<ArtifactRef>> enhancersOf(Set<ArtifactRef> targets) {
            Map<ArtifactRef, Set<ArtifactRef>> of = new TreeMap<>();
            targets.stream()
                    .filter(enhancers::containsKey)
                    .forEach(target -> of.put(target, Set.copyOf(enhancers.get(target))));
            return of;
        }
    }
}
