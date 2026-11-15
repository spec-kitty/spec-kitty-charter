package ai.speckitty.charter.profiles.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the read of one agent profile under an activation answers: the effective profile (R8; rulings D5 to D7 of
 * ADR-0078), the facts the activation gives its winner, its lineage, and the governance with the facts of each entry. It is the slice's own copy of what
 * the activations slice reads, in the slice's own vocabulary, so the application ring and the REST adapter never name the
 * activations slice.
 *
 * <p>The governance differs from the pack-scoped one ({@link ProfileRead}): it is walked over the merged closure with the
 * activation's in-force predicate, so an ancestor that is not in force is not listed and seeds nothing (the start is
 * exempt), and each entry says the state the activation gives the winner of that artifact, with the pack and level that
 * hold it. It records no {@code held}: every entry has a winner.
 *
 * @param profile the effective profile: the winner merged with its in-force enhancers and folded under its in-force
 *     ancestors, each merged with its own; for a profile that enhances another, the winner as its pack stores it
 * @param activationId the activation's id
 * @param revision the activation's revision token
 * @param governanceFingerprint the digest of the packs of the activation's closure alone
 * @param sourceFingerprint the whole-source digest the one verified read agreed on, the answer's
 *     {@code Charter-Source-Fingerprint}
 * @param winner the facts of the profile itself
 * @param governance the entries, in URN order: the artifacts in force the walk lists and the artifacts out of force that
 *     a listed artifact suggests
 * @param linkableKinds the kinds that have a pack-scoped read, so that an entry is linked only when it can be followed
 * @param lineage the in-force {@code specializes_from} ancestors, nearest first; empty for a root
 * @param enhances the ids of the agent profiles the profile enhances, in id order; empty when it enhances none
 */
public record EffectiveProfileRead(
        AgentProfile profile,
        String activationId,
        String revision,
        String governanceFingerprint,
        String sourceFingerprint,
        Facts winner,
        List<Entry> governance,
        List<ArtifactKind> linkableKinds,
        List<Lineage> lineage,
        List<String> enhances) {

    /**
     * Checks and copies the fields.
     *
     * @param profile the profile
     * @param activationId the activation
     * @param revision the activation's revision
     * @param governanceFingerprint the closure's digest
     * @param sourceFingerprint the verified source digest
     * @param winner the profile's facts
     * @param governance the entries
     * @param linkableKinds the linkable kinds
     * @param lineage the ancestors
     * @param enhances the enhanced profiles
     */
    public EffectiveProfileRead {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(activationId, "activationId");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(governanceFingerprint, "governanceFingerprint");
        Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        Objects.requireNonNull(winner, "winner");
        governance = List.copyOf(Objects.requireNonNull(governance, "governance"));
        linkableKinds = List.copyOf(Objects.requireNonNull(linkableKinds, "linkableKinds"));
        lineage = List.copyOf(Objects.requireNonNull(lineage, "lineage"));
        enhances = List.copyOf(Objects.requireNonNull(enhances, "enhances"));
    }

    /**
     * One ancestor of the profile's lineage chain (ruling D6).
     *
     * @param id the ancestor's id
     * @param pack the configured identifier of the pack that holds the version in force
     * @param depth the length of the longest in-force {@code specializes_from} path from the profile, at least 1
     */
    public record Lineage(String id, String pack, int depth) {

        /**
         * Checks the fields.
         *
         * @param id the ancestor
         * @param pack the holding pack
         * @param depth the longest path length
         */
        public Lineage {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(pack, "pack");
            if (depth < 1) {
                throw new IllegalArgumentException("an ancestor is at least one hop from the profile: " + depth);
            }
        }
    }

    /**
     * The facts an activation gives an artifact: the winner of the layer merge, and the state it has.
     *
     * @param urn the artifact; for a governance entry that lists a term, the term's address, with its context's facts
     * @param pack the configured identifier of the pack that holds the version in force
     * @param level that pack's level
     * @param state in force ({@code active}), out of force ({@code inactive}) or denied ({@code excluded}), in the
     *     contract's spelling
     * @param required whether a governing preset requires it or a pack locks it
     * @param policy the mutability level of the version in force, in the contract's spelling
     * @param reason the rule that decided the state, in the contract's spelling
     * @param satisfiedBy every artifact in force that satisfies a requirement this one is out of force for, by the level
     *     of its winning pack and then by URN (operator ruling of 2026-11-12 on #439); empty when none does
     * @param suggestion the winner's own effective suggestion, as the effective list gives it: present only for an
     *     artifact out of force that is reached only through a {@code suggests} edge of an artifact in force. The facts
     *     of a governance entry leave it empty: an entry says the words of its edge in {@link Entry#edge()}
     */
    public record Facts(
            Urn urn,
            PackIdentifier pack,
            PackLevel level,
            String state,
            boolean required,
            String policy,
            String reason,
            List<Urn> satisfiedBy,
            Optional<EdgeWords> suggestion) {

        /**
         * Checks the fields.
         *
         * @param urn the artifact
         * @param pack the holding pack
         * @param level its level
         * @param state the state
         * @param required whether it is required or locked
         * @param policy the mutability level
         * @param reason the rule that decided
         * @param satisfiedBy what satisfies its requirement
         * @param suggestion the suggesting edge's words
         */
        public Facts {
            Objects.requireNonNull(urn, "urn");
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(policy, "policy");
            Objects.requireNonNull(reason, "reason");
            satisfiedBy = List.copyOf(Objects.requireNonNull(satisfiedBy, "satisfiedBy"));
            Objects.requireNonNull(suggestion, "suggestion");
        }

        /**
         * The same facts for another URN: a term listed with the facts of its glossary context, whose state it has
         * (operator ruling of 2026-11-14).
         *
         * @param other the URN, {@code term:<context-id>/<term-id>}
         * @return the facts with {@code urn} replaced
         */
        public Facts of(Urn other) {
            return new Facts(other, pack, level, state, required, policy, reason, satisfiedBy, suggestion);
        }
    }

    /**
     * One artifact the governance walk lists, with the facts the activation gives it.
     *
     * @param facts the artifact and its facts
     * @param step the step that listed it, its earliest
     * @param via its parent in the walk: the profile, or another entry of the answer
     * @param hops the length of the path from the profile, one more than the hops of {@code via}; at least 1
     * @param edge the words of the edge that listed it, whatever its relation and whether the artifact is in force or
     *     not; {@link EdgeWords#NONE} when that edge states neither. Where several edges could reach the entry, it is
     *     the edge of the parent the walk keeps (see {@link EdgeWords})
     */
    public record Entry(Facts facts, GovernanceEntry.Step step, Urn via, int hops, EdgeWords edge) {

        /**
         * Checks the fields.
         *
         * @param facts the artifact's facts
         * @param step the step
         * @param via the parent
         * @param hops the path length
         * @param edge the words of the listing edge
         * @throws IllegalArgumentException when {@code hops} is below 1
         */
        public Entry {
            Objects.requireNonNull(facts, "facts");
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(via, "via");
            Objects.requireNonNull(edge, "edge");
            if (hops < 1) {
                throw new IllegalArgumentException("an entry is at least one hop from the profile: " + hops);
            }
        }

        /**
         * The relation of the edge that listed the entry.
         *
         * @return the step's relation
         */
        public RelationType relation() {
            return step.relation();
        }
    }

    /**
     * Whether an entry has a pack-scoped read to link to: its kind is one the generic artifact route serves. Every entry
     * has a winner and so a pack that holds it, whatever its state.
     *
     * @param entry an entry of this answer
     * @return true for an entry of a linkable kind
     */
    public boolean linksArtifact(Entry entry) {
        Objects.requireNonNull(entry, "entry");
        return entry.facts()
                .urn()
                .kind()
                .artifactKind()
                .filter(linkableKinds::contains)
                .isPresent();
    }
}
