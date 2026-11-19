package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.MissionStepLinkRule;
import ai.speckitty.charter.packs.domain.MissionTypeFacts;
import ai.speckitty.charter.packs.domain.Preset;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The persistence seam of a {@code DELETE} of an artifact or of a relationship (ADR-0065 D4, IC-06): an application-ring
 * <em>driven port</em>, so the removal services depend on neither the file system nor the commit store. Every method
 * runs under {@link #exclusively}, which takes the registry write lock and then the pack's own, in that order.
 */
public interface PackRemovalStore extends PackRevisions {

    /**
     * What the pack's author wrote into its graph documents: every graph document but the registered shard,
     * {@code registered.graph.yaml}.
     *
     * @param declared the nodes the documents declare, as URNs
     * @param edges the typed edges the documents hold; an entry that cannot be typed is left out
     */
    record Authored(Set<Urn> declared, List<Relationship> edges) {

        /** Nothing authored. */
        public static final Authored NONE = new Authored(Set.of(), List.of());

        /**
         * Copies the collections.
         *
         * @param declared the declared nodes
         * @param edges the typed edges
         */
        public Authored {
            declared = Set.copyOf(declared);
            edges = List.copyOf(edges);
        }

        /**
         * Whether the author wrote the artifact into a graph document: declared its node, or wrote an edge that starts at
         * it. A mission type's own step links ({@link MissionStepLinkRule#isOwnStepLink}) do not count: the removal drops
         * them with the type (operator ruling DM-01M7CF9PA1V2DS0P7ZZVANJ1WZ).
         *
         * @param artifact the artifact
         * @param packName the name composition knows the pack by
         * @return whether a document the {@code DELETE} would have to rewrite names it
         */
        public boolean names(Urn artifact, String packName) {
            return declared.contains(artifact)
                    || edges.stream()
                            .anyMatch(edge -> edge.from().equals(artifact)
                                    && !MissionStepLinkRule.isOwnStepLink(packName, artifact, edge));
        }
    }

    /**
     * What one removal leaves behind: the files of the artifacts go, the registered relationships and the presets are the
     * ones given, nothing else of the pack is rewritten.
     *
     * @param artifacts the artifacts whose files and companions are removed, together: one artifact, or a mission type
     *     and its skeletons (D10 of #99); empty for the removal of a relationship
     * @param relationships the complete registered-relationship set to persist, in place of the stored one
     * @param presets the presets to rewrite, whole, with the items that name the artifacts left out; one that holds no
     *     list any longer is written with none
     * @param droppedLinks a removed mission type's own step links and the scope edges from its own step actions, which
     *     go from the author-written documents that hold them; empty when no mission type is removed
     */
    record Removal(
            List<Urn> artifacts,
            List<Relationship> relationships,
            List<Preset> presets,
            List<Relationship> droppedLinks) {

        /**
         * Checks the parts and copies the lists.
         *
         * @param artifacts the removed artifacts, if any
         * @param relationships the registered relationships that stay
         * @param presets the presets to rewrite
         * @param droppedLinks a removed mission type's own step links and the scope edges from its own step actions,
         *     which go from the author-written graph documents that hold them (operator rulings
         *     DM-01M7CF9PA1V2DS0P7ZZVANJ1WZ and DM-01M7DTNBMQ6Y88RKJWD3ZFH6G6); none for any other removal
         */
        public Removal {
            artifacts = List.copyOf(artifacts);
            relationships = List.copyOf(relationships);
            presets = List.copyOf(presets);
            droppedLinks = List.copyOf(droppedLinks);
        }

        /**
         * The removal of an artifact.
         *
         * @param artifact the artifact
         * @param relationships the registered relationships that stay
         * @param presets the presets to rewrite
         * @return the removal
         */
        public static Removal ofArtifact(Urn artifact, List<Relationship> relationships, List<Preset> presets) {
            return ofArtifacts(List.of(artifact), relationships, presets, List.of());
        }

        /**
         * The removal of artifacts that go together, as one removal.
         *
         * @param artifacts the artifacts, the one the request names first
         * @param relationships the registered relationships that stay
         * @param presets the presets to rewrite
         * @param droppedLinks the removed mission types' own step links that go from the author-written documents
         * @return the removal
         */
        public static Removal ofArtifacts(
                List<Urn> artifacts,
                List<Relationship> relationships,
                List<Preset> presets,
                List<Relationship> droppedLinks) {
            if (artifacts.isEmpty()) {
                throw new IllegalArgumentException("a removal of artifacts names at least one");
            }
            return new Removal(artifacts, relationships, presets, droppedLinks);
        }

        /**
         * The removal of a registered relationship.
         *
         * @param relationships the registered relationships that stay
         * @return the removal
         */
        public static Removal ofRelationship(List<Relationship> relationships) {
            return new Removal(List.of(), relationships, List.of(), List.of());
        }
    }

    /**
     * The name a pack is configured under, for a pack addressed by its registered id.
     *
     * @param pack the pack's identity
     * @return the configured name
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code pack_not_found} when no configured pack matches
     */
    PackIdentifier configuredName(PackIdentifier pack);

    /**
     * Runs a removal's whole read-check-commit sequence holding the registry lock and then the pack's lock.
     *
     * @param pack the pack's identity
     * @param work the sequence
     * @param <T> what it answers
     * @return what {@code work} returns
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code pack_not_found} when no configured pack matches
     * @throws RegistryWriteLock.RegistryBusyException when the registry lock was not free in time
     */
    <T> T exclusively(PackIdentifier pack, Supplier<T> work);

    /**
     * The registered relationships the pack's shard holds.
     *
     * @param pack the pack's identity
     * @return the edges, in registration order
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code pack_unreadable} when the shard does not bind
     */
    List<Relationship> registeredRelationships(PackIdentifier pack);

    /**
     * What the author wrote into the pack's graph documents.
     *
     * @param pack the pack's identity
     * @return the declared nodes and the typed edges
     */
    Authored authored(PackIdentifier pack);

    /**
     * The facts of the pack's stored mission types: their steps and the substeps of each, which a relationship removal
     * judges the {@code scope} edges a substep's candidates need against (FR-005).
     *
     * @param pack the pack's identity
     * @return the facts, one per stored mission type
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code pack_unreadable} when the pack cannot be read
     */
    List<MissionTypeFacts> missionTypes(PackIdentifier pack);

    /**
     * Removes what the removal names and swaps the result in whole, so that a concurrent read sees the old pack or the new
     * one. The version history is kept and gets no entry: a removal registers no version.
     *
     * @param pack the pack's identity
     * @param removal what to remove and what to leave
     * @return the revision of the stored state
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code pack_unreadable} when the swap fails; the pack is
     *     unchanged
     */
    PackRevision remove(PackIdentifier pack, Removal removal);

    /**
     * Removes the pack whole (ADR-0081 D8): its directory is renamed away in one step under the write generation and then
     * deleted, so a concurrent read sees the pack or no pack. The version history is kept, with the stored pair healed
     * into it (D9). It takes the locks itself when the caller does not hold them; a removal's caller runs it inside
     * {@link #exclusively}, whose locks are re-entrant.
     *
     * @param pack the pack's identity
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code registration_conflict} for a pack in a read-only
     *     location or a linked pack; {@code pack_unreadable} when the rename fails, the pack then whole
     */
    void removeWhole(PackIdentifier pack);

    /**
     * Refuses a pack that {@link #removeWhole} would not remove: one in a read-only location, or a linked pack (ADR-0081
     * P9). A removal judges it before its revision, so a dry run answers it too.
     *
     * @param pack the pack's identity
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code registration_conflict}
     */
    void requireRemovable(PackIdentifier pack);
}
