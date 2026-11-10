package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ActionContextWalk.SuggestionCandidate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * A profile's governance with the pack that holds each artifact it lists: the answer of both entry points of the
 * governance walk ({@link GovernanceWalk}). The activation-scoped one is {@link ClosureView#governance}, over the
 * merged closure and the in-force predicate, and names the winner. The pack-scoped one, {@link #ofPack}, walks one
 * pack's composed {@code depends_on} closure with every node in force and names the first holder in closure order.
 *
 * <p>The pack-scoped read is one piece of a larger read: the caller opens the {@link ConsistentRead#read verified read}
 * and runs the body read and this walk in its supplier, so the body, the closure and the walk name the same inputs
 * (fold W5). {@link #ofPack} opens no read of its own and tries nothing again.
 */
@Service
public final class ProfileGovernance {

    /**
     * One artifact the walk lists, how it was reached, and who holds it.
     *
     * @param urn the artifact
     * @param step the step that listed it
     * @param via its parent: the profile or another entry of the answer
     * @param hops the length of the path from the profile
     * @param when the {@code when} of the edge that reached it, whatever its relation; empty when the edge states none
     *     (see {@link GovernanceWalk.Entry})
     * @param reason the {@code reason} of that edge; empty likewise
     * @param held whether a pack of the closure holds the artifact; false only in the pack-scoped walk, for a target
     *     no pack holds, which has no holder
     * @param holder the holding pack: the winner under an activation, the first holder in closure order for a pack;
     *     empty when {@code held} is false
     */
    public record Entry(
            Urn urn,
            GovernanceWalk.Step step,
            Urn via,
            int hops,
            Optional<String> when,
            Optional<String> reason,
            boolean held,
            Optional<ClosureView.Holder> holder) {

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
         * @param holder the holding pack
         */
        public Entry {
            Objects.requireNonNull(urn, "urn");
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(via, "via");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(holder, "holder");
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
     * What a profile's governance walk answers, with holders.
     *
     * @param profile the profile's URN
     * @param entries the entries, sorted by full URN string
     * @param suggestionCandidates the out-of-force artifacts the {@code suggests} step reached that the closure holds
     *     (activation-scoped only), in URN order
     */
    public record Governance(Urn profile, List<Entry> entries, List<SuggestionCandidate> suggestionCandidates) {

        /**
         * Checks and copies the fields.
         *
         * @param profile the profile
         * @param entries the entries
         * @param suggestionCandidates the out-of-force suggestions
         */
        public Governance {
            Objects.requireNonNull(profile, "profile");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            suggestionCandidates = List.copyOf(Objects.requireNonNull(suggestionCandidates, "suggestionCandidates"));
        }

        /** The walk's result with each held entry given the holder {@code holderOf} names. */
        static Governance of(GovernanceWalk.Result result, Function<Urn, Optional<ClosureView.Holder>> holderOf) {
            List<Entry> entries = result.entries().stream()
                    .map(walked -> new Entry(
                            walked.urn(),
                            walked.step(),
                            walked.via(),
                            walked.hops(),
                            walked.when(),
                            walked.reason(),
                            walked.held(),
                            walked.held() ? holderOf.apply(walked.urn()) : Optional.empty()))
                    .toList();
            return new Governance(result.profile(), entries, result.suggestionCandidates());
        }
    }

    private final ClosureQueries queries;

    /**
     * A service over the closure queries.
     *
     * @param queries the queries that compose a pack's closure
     */
    @Autowired
    public ProfileGovernance(ClosureQueries queries) {
        this.queries = Objects.requireNonNull(queries, "queries");
    }

    /**
     * A profile's governance over one pack's composed {@code depends_on} closure, merged, with every node in force and
     * the edges written to a target no pack holds (fold W2, W5). Call it inside the supplier of a {@link
     * ConsistentRead#read}: it composes the closure without a read of its own ({@link ClosureQueries#snapshotWithin}),
     * so the caller's two digests judge it with whatever else the supplier read.
     *
     * @param pack the configured pack whose closure is walked
     * @param profileId the profile's id
     * @param depth the number of {@code suggests} hops, at least 0
     * @return the governance, with the first holder of each held entry in closure order; empty when no pack of the
     *     closure holds the profile
     * @throws CharterRefusal as the compose and the layer merge refuse the closure, unchanged
     * @throws UnknownRootException when {@code pack} is not a configured pack
     * @throws IllegalArgumentException when {@code depth} is negative
     */
    public Optional<Governance> ofPack(PackIdentifier pack, String profileId, int depth) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(profileId, "profileId");
        ClosureView view = queries.snapshotWithin(Optional.of(List.of(pack)));
        view.merged();
        if (!view.holds(ArtifactKind.AGENT_PROFILE, profileId)) {
            return Optional.empty();
        }
        return Optional.of(view.packGovernance(new Urn(NodeKind.AGENT_PROFILE, profileId), depth));
    }
}
