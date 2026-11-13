package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The references the winner an activation named writes (research R10, #532): the pack read of the references of the
 * winner's pack ({@code listArtifactReferences}), read against the source fingerprint of the activation's read. It is
 * the port of the edge (ADR-0037 as amended 2026-11-09): the adapter is {@code artifacts.infra.packs}, the one place of
 * the edge that asks the packs slice, and it maps the pack read's references one for one, so the twin answers what the
 * pack read answers for the winner's pack and nothing it computed itself.
 */
public interface ReferenceReads {

    /**
     * The references one artifact of a pack writes, as the pack read answers them, read at a fingerprint the caller
     * holds.
     *
     * @param pack the configured identifier of the pack that holds the winner
     * @param kind the winner's kind
     * @param id the winner's id
     * @param expectedFingerprint the whole-source fingerprint of the activation's read
     * @return the references, in the order the pack read gives them
     * @throws CharterRefusal with {@code stale_input} when the read verified another fingerprint, or the pack or the
     *     artifact is gone since the activation named it; otherwise as the pack read refuses
     */
    List<Reference> references(PackIdentifier pack, ArtifactKind kind, String id, String expectedFingerprint);

    /**
     * One reference, as the pack read resolves it through the compose.
     *
     * @param written the target as the graph document writes it, bare or pack-qualified
     * @param relation the relation of its edge, or of the relation the artifact derives
     * @param urn the bare URN it resolves to
     * @param holder the pack of the compose that holds what it names; empty when no pack holds it
     * @param listed the kind of the target when the pack-scoped reads list it, which the pack read links; empty
     *     otherwise
     */
    record Reference(
            PackQualifiedReference written,
            RelationType relation,
            Urn urn,
            Optional<Holder> holder,
            Optional<ArtifactKind> listed) {

        /**
         * Checks the fields.
         *
         * @param written the written target
         * @param relation the relation
         * @param urn the URN
         * @param holder the holding pack
         * @param listed the listed kind
         */
        public Reference {
            Objects.requireNonNull(written, "written");
            Objects.requireNonNull(relation, "relation");
            Objects.requireNonNull(urn, "urn");
            Objects.requireNonNull(holder, "holder");
            Objects.requireNonNull(listed, "listed");
        }
    }

    /**
     * The pack that holds what a reference names.
     *
     * @param packId its configured identifier
     * @param descriptorPackId the {@code pack_id} of its {@code pack.yaml}
     * @param name its name
     */
    record Holder(PackIdentifier packId, String descriptorPackId, String name) {

        /**
         * Checks the fields.
         *
         * @param packId the configured identifier
         * @param descriptorPackId the descriptor's pack id
         * @param name the name
         */
        public Holder {
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(descriptorPackId, "descriptorPackId");
            Objects.requireNonNull(name, "name");
        }
    }
}
