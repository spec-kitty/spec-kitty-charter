package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ActionContextWalk.Engine;
import ai.speckitty.charter.packs.application.ActionContextWalk.SuggestionCandidate;
import ai.speckitty.charter.packs.application.ActionContextWalk.WalkStep;
import ai.speckitty.charter.packs.application.EdgeIndex.Link;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The profile's governance walk: the action-context walk's rules ({@link ActionContextWalk}), from
 * {@code agent_profile:<id>} instead of an action node. Five breadth-first steps over the {@link ActionContextWalk.Engine
 * shared engine}, each visiting a node at most once, with minimum-step semantics:
 *
 * <ol>
 *   <li>lineage: {@code specializes_from}, transitive, through the predicate;
 *   <li>requires: the {@code requires} of the profile and of the ancestors listed, transitive;
 *   <li>suggests: their {@code suggests}, {@code depth} hops;
 *   <li>requires of the suggested artifacts, transitive;
 *   <li>vocabulary: one hop from the profile and from everything steps 1 to 4 list (the profile's own vocabulary is
 *       listed).
 * </ol>
 *
 * <p>The predicate decides what is in force. A node it refuses is neither listed nor walked through, so under an
 * activation an ancestor that is not in force is not listed and seeds nothing, where the cascade passes through it
 * (operator ruling, decision {@code 01M7115WE6WQ7Q41HSS9ZHD1F3}). The start is exempt, as the action's start is: a
 * profile is walked whatever its own state. An artifact the {@code suggests} step reaches that the closure holds and the
 * predicate refuses is a candidate, reported and never expanded, as the action walk reports it. Entries carry no
 * enhancers: a profile takes no step enhancement.
 *
 * <p>A walk is over the links of one index. {@link #over} walks the links between held artifacts, which is what the
 * activation-scoped read wants. {@link #overWritten} walks every edge a held artifact writes, each resolved as the
 * activation walk resolves it: an edge to a place that holds nothing is listed with {@link Entry#held() held}
 * {@code false}, so the pack-scoped read can say that a parent is declared and not there. A URN reached by both a held
 * and a dangling edge takes the parent the usual order picks (fewest hops, smallest parent, then the smallest edge:
 * {@code when}, then {@code reason}, then a held edge before a dangling one), and {@code held} follows that parent's
 * edge. Entries are sorted by full URN string, whatever step listed them.
 *
 * <p>An entry listed with {@code held} false is never walked from (fold W2): it seeds no later step, and a later step
 * that reaches it again through a held edge does not walk through it, even where another pack of the closure holds the
 * same URN with edges of its own. Its edges are not the profile's: the edge that listed it leads to a place that holds
 * nothing. So an entry with {@code held} false has nothing below it, and no entry names it as its {@code via}.
 * Minimum-step semantics list a URN once, at the earliest step that reaches it, so such an entry keeps {@code held}
 * false even when a later step reaches the URN through a held edge.
 */
public final class GovernanceWalk {

    /** One of the five steps, with the relation its entries are listed by. It reaches no wire: the wire has its own. */
    public enum Step implements WalkStep {
        /** The ancestors: {@code specializes_from}, transitive. */
        LINEAGE(RelationType.SPECIALIZES_FROM),
        /** What the profile and its ancestors require, transitively. */
        REQUIRES(RelationType.REQUIRES),
        /** What the profile and its ancestors suggest, {@code depth} hops. */
        SUGGESTS(RelationType.SUGGESTS),
        /** What the suggested artifacts require, transitively. */
        REQUIRES_OF_SUGGESTED(RelationType.REQUIRES),
        /** One hop of vocabulary from the profile and from everything the earlier steps list. */
        VOCABULARY(RelationType.VOCABULARY);

        private final RelationType relation;

        Step(RelationType relation) {
            this.relation = relation;
        }

        /**
         * The relation the step's entries are listed by (the entries of the requires of the suggested artifacts are
         * {@code requires}).
         *
         * @return the relation
         */
        @Override
        public RelationType relation() {
            return relation;
        }
    }

    /**
     * One artifact the walk lists, and how it was reached.
     *
     * @param urn the artifact
     * @param step the step that listed it
     * @param via its parent: the profile or another entry of the answer
     * @param hops the length of the path from the profile, {@code hops(via) + 1}
     * @param when the {@code when} of the edge that reached it, whatever its relation; empty when that edge states
     *     none. Where several edges could reach the entry, it is the edge of the parent the walk keeps: the first
     *     reach, by the fewest hops, then the smallest parent, then the smallest edge. A later step, or another edge,
     *     that would also reach the entry does not change it
     * @param reason the {@code reason} of that edge; empty likewise
     * @param held whether the place the listing edge leads to holds the artifact: false only in a walk over written
     *     links, for an edge to a place that holds nothing, which is listed and never walked from by any step
     */
    public record Entry(
            Urn urn, Step step, Urn via, int hops, Optional<String> when, Optional<String> reason, boolean held) {

        /**
         * Checks the fields.
         *
         * @param urn the artifact
         * @param step the step
         * @param via the parent
         * @param hops the path length
         * @param when the reaching edge's condition
         * @param reason the reaching edge's reason
         * @param held whether the closure holds the artifact
         */
        public Entry {
            Objects.requireNonNull(urn, "urn");
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(via, "via");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
        }

        /**
         * The relation the entry is listed by.
         *
         * @return the step's relation
         */
        public RelationType relation() {
            return step.relation();
        }
    }

    /**
     * What the walk answers.
     *
     * @param profile the start node
     * @param entries the entries, sorted by full URN string
     * @param suggestionCandidates the out-of-force artifacts the {@code suggests} step reached that the closure holds,
     *     in URN order; empty when every node is in force
     */
    public record Result(Urn profile, List<Entry> entries, List<SuggestionCandidate> suggestionCandidates) {

        /**
         * Checks and copies the fields.
         *
         * @param profile the start node
         * @param entries the entries
         * @param suggestionCandidates the out-of-force suggestions
         */
        public Result {
            Objects.requireNonNull(profile, "profile");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            suggestionCandidates = List.copyOf(Objects.requireNonNull(suggestionCandidates, "suggestionCandidates"));
        }
    }

    private final Map<Urn, List<Link>> outgoing;

    private GovernanceWalk(List<Link> links) {
        Map<Urn, List<Link>> bySource = new HashMap<>();
        for (Link link : links) {
            bySource.computeIfAbsent(link.source(), ignored -> new ArrayList<>())
                    .add(link);
        }
        this.outgoing = bySource;
    }

    /**
     * A walk over the links between held artifacts of an index: every node it lists is held. This is the
     * activation-scoped walk's, over the merged index.
     *
     * @param index the index, merged for an activation
     * @return the walk
     */
    public static GovernanceWalk over(EdgeIndex index) {
        return new GovernanceWalk(Objects.requireNonNull(index, "index").links());
    }

    /**
     * A walk over every edge a held artifact of the index writes (fold W2): a target the edge's place does not hold is
     * listed with {@code held} false and never walked from, by any step. This is the pack-scoped walk's.
     *
     * @param index the index, merged for the pack's composed closure
     * @return the walk
     */
    public static GovernanceWalk overWritten(EdgeIndex index) {
        Objects.requireNonNull(index, "index");
        return new GovernanceWalk(index.writtenLinks());
    }

    /**
     * Walks a profile's governance.
     *
     * @param profile the start node, {@code agent_profile:<id>}; exempt from the predicate
     * @param depth the number of {@code suggests} hops, at least 0
     * @param inForce whether an artifact is in force: the effective set under an activation, every node for a pack
     * @return what the profile reaches. Beside the entries, {@link Result#suggestionCandidates()} holds each target of
     *     a {@code suggests} edge of step 3 that the index holds and the predicate refuses, unless the profile or a
     *     listed artifact also reaches it through a hard relation
     * @throws IllegalArgumentException when {@code depth} is negative
     */
    public Result walk(Urn profile, int depth, Predicate<Urn> inForce) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(inForce, "inForce");
        if (depth < 0) {
            throw new IllegalArgumentException("depth counts suggests hops and is at least 0: " + depth);
        }
        Engine<Step> run = new Engine<>(profile, inForce, outgoing);
        List<Urn> ancestors = run.expand(List.of(profile), Step.LINEAGE, Integer.MAX_VALUE);
        List<Urn> seeds = new ArrayList<>(ancestors);
        seeds.add(profile);
        run.expand(seeds, Step.REQUIRES, Integer.MAX_VALUE);
        List<Urn> suggested = run.expand(seeds, Step.SUGGESTS, depth);
        run.expand(suggested, Step.REQUIRES_OF_SUGGESTED, Integer.MAX_VALUE);
        List<Urn> everything = new ArrayList<>(run.listedUrns());
        everything.add(profile);
        run.expand(everything, Step.VOCABULARY, 1);
        List<Entry> entries = run.listed().stream()
                .map(item -> new Entry(
                        item.urn(), item.step(), item.via(), item.hops(), item.when(), item.reason(), item.held()))
                .toList();
        return new Result(profile, entries, run.suggestions());
    }
}
