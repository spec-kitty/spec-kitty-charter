package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ActionContext;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.EffectiveArtifactRead;
import ai.speckitty.charter.activations.domain.GraphSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private Function<InForceQuery, GraphSnapshot> graphAnswer = query -> GraphSnapshot.empty();
    private Function<ContextAsk, ActionContext> contextAnswer = ask -> ActionContext.notInForce();
    private final List<ContextAsk> contextAsks = new ArrayList<>();
    private int graphReads;
    private final List<String> nameChecks = new ArrayList<>();
    private Function<EffectiveAsk, Optional<EffectiveArtifactRead>> winnerAnswer = ask -> Optional.empty();
    private final List<EffectiveAsk> effectiveAsks = new ArrayList<>();
    private Function<ArtifactKind, List<EffectiveHeldWinner>> heldAnswer = kind -> List.of();
    private final List<ArtifactKind> heldAsks = new ArrayList<>();
    private List<ArtifactKind> listedKinds = List.of(
            ArtifactKind.TACTIC,
            ArtifactKind.DIRECTIVE,
            ArtifactKind.STYLEGUIDE,
            ArtifactKind.PARADIGM,
            ArtifactKind.TOOLGUIDE,
            ArtifactKind.AGENT_PROFILE);

    /**
     * What a session was asked of the action context.
     *
     * @param missionType the mission type
     * @param action the action
     * @param depth the depth
     * @param query the document's request
     */
    public record ContextAsk(String missionType, String action, int depth, InForceQuery query) {}

    /**
     * What a session was asked of the winner read.
     *
     * @param kind the artifact's kind
     * @param id the artifact's id
     * @param depth the number of {@code suggests} hops of a profile's governance
     * @param query the document's request
     */
    public record EffectiveAsk(ArtifactKind kind, String id, int depth, InForceQuery query) {}

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

    /**
     * Answers every graph question of a session with this graph from now on.
     *
     * @param next the answers of {@link PacksSession#graph}, by query
     */
    public void graphs(Function<InForceQuery, GraphSnapshot> next) {
        this.graphAnswer = next;
    }

    /**
     * Answers every action-context question of a session with this walk from now on; a function that throws a refusal
     * answers as the packs slice does for an unknown name.
     *
     * @param next the answers of {@link PacksSession#actionContext}, by question
     */
    public void contexts(Function<ContextAsk, ActionContext> next) {
        this.contextAnswer = next;
    }

    /**
     * Answers every winner read of a session with this function from now on; a function that throws a refusal answers
     * as the packs slice does for a stale input.
     *
     * @param next the answers of {@link PacksSession#effectiveArtifact}, by question
     */
    public void winners(Function<EffectiveAsk, Optional<EffectiveArtifactRead>> next) {
        this.winnerAnswer = next;
    }

    /**
     * The winner reads the sessions were asked, in order.
     *
     * @return the questions
     */
    public List<EffectiveAsk> effectiveAsks() {
        return List.copyOf(effectiveAsks);
    }

    /**
     * Answers every held-winner read of a session with this function from now on; a function that throws a refusal
     * answers as the packs slice does for a merge it refuses.
     *
     * @param next the answers of {@link PacksSession#heldWinners}, by kind; {@link PacksSession#heldWinner} filters them
     */
    public void heldWinners(Function<ArtifactKind, List<EffectiveHeldWinner>> next) {
        this.heldAnswer = next;
    }

    /**
     * The kinds the sessions were asked the held winners of, in order, an item read included.
     *
     * @return the kinds
     */
    public List<ArtifactKind> heldAsks() {
        return List.copyOf(heldAsks);
    }

    /**
     * Lists these kinds from now on.
     *
     * @param next the answer of {@link AvailablePacks#listedKinds}
     */
    public void listedKinds(List<ArtifactKind> next) {
        this.listedKinds = List.copyOf(next);
    }

    @Override
    public List<ArtifactKind> listedKinds() {
        return listedKinds;
    }

    /**
     * The action-context questions the sessions were asked, in order.
     *
     * @return the questions
     */
    public List<ContextAsk> contextAsks() {
        return List.copyOf(contextAsks);
    }

    /**
     * The action names the sessions were asked to judge on their own, as {@code <missionType>/<action>}, in order.
     *
     * @return the names
     */
    public List<String> nameChecks() {
        return List.copyOf(nameChecks);
    }

    /**
     * How often the sessions were asked for the graph.
     *
     * @return the number of {@link PacksSession#graph} calls
     */
    public int graphReads() {
        return graphReads;
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

        @Override
        public GraphSnapshot graph(InForceQuery query) {
            graphReads++;
            return graphAnswer.apply(query);
        }

        @Override
        public void requireAction(String missionType, String action) {
            nameChecks.add(missionType + "/" + action);
            java.util.Objects.requireNonNull(contextAnswer.apply(new ContextAsk(
                    missionType, action, 0, new InForceQuery(Set.of(), Set.of(), Optional.empty(), Map.of()))));
        }

        @Override
        public Optional<EffectiveArtifactRead> effectiveArtifact(
                ArtifactKind kind, String id, int depth, InForceQuery query) {
            EffectiveAsk ask = new EffectiveAsk(kind, id, depth, query);
            effectiveAsks.add(ask);
            return winnerAnswer.apply(ask);
        }

        @Override
        public List<EffectiveHeldWinner> heldWinners(ArtifactKind kind) {
            heldAsks.add(kind);
            return heldAnswer.apply(kind);
        }

        @Override
        public Optional<EffectiveHeldWinner> heldWinner(ArtifactKind kind, String id) {
            heldAsks.add(kind);
            return heldAnswer.apply(kind).stream()
                    .filter(winner -> winner.ref().id().equals(id))
                    .findFirst();
        }

        @Override
        public ActionContext actionContext(String missionType, String action, int depth, InForceQuery query) {
            ContextAsk ask = new ContextAsk(missionType, action, depth, query);
            contextAsks.add(ask);
            return contextAnswer.apply(ask);
        }
    }
}
