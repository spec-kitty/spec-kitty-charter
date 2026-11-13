package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Payload;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The pack-scoped read of one artifact of a kind another slice renders (T019): the stored entity, the pack revision it
 * was read at and, for an agent profile, its governance, all inside ONE {@link ConsistentRead#read verified read} (fold
 * W5). The body, the closure and the walk therefore name the same inputs, and the fingerprint the answer carries is the
 * whole-source digest that read verified, the one every other built read reports as {@code Charter-Source-Fingerprint}
 * (never the closure's own digest, which {@link ClosureView#fingerprint()} holds for a view).
 *
 * <p>It lives apart from {@link ArtifactReadService} because the generic item read renders none of these kinds' bodies:
 * this read is kind-neutral and asks nothing of the generic route's kind table, except which kinds a client can follow
 * ({@link ArtifactReadService#listed()}). It reuses the generic read's collaborators: the pack port, the
 * descriptor port, {@link ConsistentRead}, {@link PackRevisions} and {@link ProfileGovernance} (which composes the
 * pack's {@code depends_on} closure through {@link ClosureQueries#snapshotWithin}).
 *
 * <p>The pack is judged first, as the generic item read judges it: an identifier no configured pack answers to is
 * {@link RefusalCode#PACK_NOT_FOUND}, and a configured pack without a readable {@code pack.yaml} is {@link
 * RefusalCode#PACK_UNREADABLE}. An artifact the pack itself does not hold is {@link RefusalCode#ARTIFACT_NOT_FOUND}, an
 * artifact only a pack of its closure holds included: the read is of this pack's artifact. A closure that cannot
 * compose or merge refuses the read with the composition code the compose refuses with, unchanged. A change of the
 * inputs during the read is {@link RefusalCode#STALE_INPUT}, which beats a not-found.
 *
 * <p>Beside the item, it answers a pack's artifacts of one kind ({@link #held}, the per-kind lists of the kinds another
 * slice renders) and an asset with its payload's bytes ({@link #payload(PackIdentifier, String)}, #105), each in one
 * verified read with the same pack check; the payload read against a fingerprint the caller already holds refuses
 * {@link RefusalCode#STALE_INPUT} when the read verified another (research R15).
 */
@Service
public final class ForeignArtifactRead {

    private static final String STALE = "The inputs changed while the artifact was read; read it again.";

    private final PackRepository packs;
    private final PackDescriptorRepository descriptors;
    private final PackCatalogRepository catalog;
    private final ConsistentRead read;
    private final PackRevisions revisions;
    private final ProfileGovernance governance;

    /**
     * What one read answers.
     *
     * @param artifact the stored entity
     * @param revision the pack revision it was read at, the answer's {@code ETag}
     * @param governance the pack-scoped governance, for an agent profile; empty for any other kind
     * @param servedKinds the kinds the generic route lists, so the caller can tell which entries it may link without
     *     naming this slice
     */
    public record Foreign(
            CharterArtifact artifact,
            PackRevision revision,
            Optional<ProfileGovernance.Governance> governance,
            List<ArtifactKind> servedKinds) {

        /**
         * Checks and copies the fields.
         *
         * @param artifact the entity
         * @param revision the revision
         * @param governance the governance
         * @param servedKinds the generic route's kinds
         */
        public Foreign {
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(governance, "governance");
            servedKinds = List.copyOf(Objects.requireNonNull(servedKinds, "servedKinds"));
        }
    }

    /**
     * What a read of a pack's artifacts of one kind answers.
     *
     * @param artifacts the pack's own artifacts of the kind, in the pack's stable order
     * @param revision the pack revision they were read at, the answer's {@code ETag}
     */
    public record Held(List<CharterArtifact> artifacts, PackRevision revision) {

        /**
         * Checks and copies the fields.
         *
         * @param artifacts the artifacts
         * @param revision the revision
         */
        public Held {
            artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
            Objects.requireNonNull(revision, "revision");
        }
    }

    /**
     * What a read of an asset's payload answers.
     *
     * @param asset the asset, its sidecar's descriptor
     * @param payload the exact bytes of the payload file the asset names
     * @param revision the pack revision both were read at, the answer's {@code ETag}
     */
    public record AssetPayload(Asset asset, Payload payload, PackRevision revision) {

        /**
         * Checks the fields.
         *
         * @param asset the asset
         * @param payload the bytes
         * @param revision the revision
         */
        public AssetPayload {
            Objects.requireNonNull(asset, "asset");
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(revision, "revision");
        }
    }

    /**
     * A read over the generic read's collaborators.
     *
     * @param packs the port that loads a pack aggregate
     * @param descriptors the port that reads a pack's descriptor, judged before the artifact is looked up
     * @param catalog the configured packs, to name the pack the request addresses by its declared {@code pack_id}
     * @param read the verifier that digests the inputs before and after the read
     * @param revisions the pack revision, read inside the verified read
     * @param governance the pack-scoped governance walk, run inside the verified read
     */
    @Autowired
    public ForeignArtifactRead(
            PackRepository packs,
            PackDescriptorRepository descriptors,
            PackCatalogRepository catalog,
            ConsistentRead read,
            PackRevisions revisions,
            ProfileGovernance governance) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.read = Objects.requireNonNull(read, "read");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.governance = Objects.requireNonNull(governance, "governance");
    }

    /**
     * One artifact of a pack with its revision and, for an agent profile, its governance.
     *
     * @param pack the pack's identity, as the request names it
     * @param kind the artifact's kind
     * @param id the artifact's id within its kind
     * @param depth the number of {@code suggests} hops of a profile's governance, at least 0
     * @return the artifact, its revision, the governance and the generic route's kinds, with the whole-source
     *     fingerprint the read verified
     * @throws IllegalArgumentException when {@code depth} is negative; nothing is read
     * @throws CharterRefusal with {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has this identifier, with
     *     {@link RefusalCode#PACK_UNREADABLE} when the configured pack has no readable {@code pack.yaml} or an input
     *     cannot be read, with {@link RefusalCode#ARTIFACT_NOT_FOUND} when the pack holds no such artifact, with the
     *     composition code of a closure that cannot compose or merge, or with {@link RefusalCode#STALE_INPUT} when the
     *     inputs changed during the read
     */
    public ConsistentRead.Verified<Foreign> read(PackIdentifier pack, ArtifactKind kind, String id, int depth) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        if (depth < 0) {
            throw new IllegalArgumentException("depth counts suggests hops and is at least 0: " + depth);
        }
        return read.read(STALE, () -> {
            PackDescriptor descriptor = judged(pack);
            CharterArtifact artifact = held(packs.load(pack), pack, kind, id);
            Optional<ProfileGovernance.Governance> walked = kind == ArtifactKind.AGENT_PROFILE
                    ? Optional.of(governance
                            .ofPack(configuredId(pack, descriptor), id, depth)
                            .orElseThrow(() -> new IllegalStateException("the closure of pack " + pack.value()
                                    + " does not hold the " + kind.token() + " " + id + " the pack holds")))
                    : Optional.empty();
            return new Foreign(artifact, revisions.currentRevision(pack), walked, ArtifactReadService.listed());
        });
    }

    /**
     * The artifacts of one kind a pack holds, with the revision they were read at, in one verified read.
     *
     * @param pack the pack's identity, as the request names it
     * @param kind the artifacts' kind
     * @return the pack's own artifacts of the kind (an artifact only a pack of its closure holds is not listed) and its
     *     revision, with the whole-source fingerprint the read verified
     * @throws CharterRefusal with {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has this identifier, with
     *     {@link RefusalCode#PACK_UNREADABLE} when the configured pack has no readable {@code pack.yaml} or an input
     *     cannot be read, or with {@link RefusalCode#STALE_INPUT} when the inputs changed during the read
     */
    public ConsistentRead.Verified<Held> held(PackIdentifier pack, ArtifactKind kind) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(kind, "kind");
        return read.read(STALE, () -> {
            judged(pack);
            return new Held(packs.load(pack).artifacts(kind), revisions.currentRevision(pack));
        });
    }

    /**
     * One asset of a pack with the bytes of the payload file it names and the pack revision, in one verified read.
     *
     * @param pack the pack's identity, as the request names it
     * @param assetId the asset's id
     * @return the asset, its payload and the revision, with the whole-source fingerprint the read verified
     * @throws CharterRefusal with {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has this identifier, with
     *     {@link RefusalCode#ARTIFACT_NOT_FOUND} when the pack holds no such asset, with {@link
     *     RefusalCode#PACK_UNREADABLE} when the configured pack has no readable {@code pack.yaml}, an input cannot be
     *     read or the pack does not hold the payload file the asset names, or with {@link RefusalCode#STALE_INPUT} when
     *     the inputs changed during the read
     */
    public ConsistentRead.Verified<AssetPayload> payload(PackIdentifier pack, String assetId) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(assetId, "assetId");
        return read.read(STALE, () -> {
            judged(pack);
            Pack loaded = packs.load(pack);
            Asset asset = (Asset) held(loaded, pack, ArtifactKind.ASSET, assetId);
            Payload bytes = loaded.assetFiles().stream()
                    .filter(file -> file.path().equals(asset.path()))
                    .map(AssetFile::payload)
                    .findFirst()
                    // The aggregate guards that every asset names a held file, so this is a store fault, not a miss.
                    .orElseThrow(() -> new CharterRefusal(
                            RefusalCode.PACK_UNREADABLE,
                            "Pack " + pack.value() + " holds no payload file " + asset.path() + " for the asset "
                                    + assetId + "."));
            return new AssetPayload(asset, bytes, revisions.currentRevision(pack));
        });
    }

    /**
     * The same read, against the source fingerprint the caller already holds, so that the bytes are of the inputs the
     * caller judged (research R15: the payload of an asset under an activation, whose winner the activation named).
     *
     * @param pack the pack's identity, as the request names it
     * @param assetId the asset's id
     * @param expectedFingerprint the whole-source fingerprint the caller's own verified read agreed on
     * @return the asset, its payload and the revision, with that fingerprint
     * @throws CharterRefusal as {@link #payload(PackIdentifier, String)} refuses, and with {@link RefusalCode#STALE_INPUT}
     *     when this read verified another fingerprint: the inputs changed since the caller's read
     */
    public ConsistentRead.Verified<AssetPayload> payload(
            PackIdentifier pack, String assetId, String expectedFingerprint) {
        Objects.requireNonNull(expectedFingerprint, "expectedFingerprint");
        ConsistentRead.Verified<AssetPayload> verified = payload(pack, assetId);
        if (!verified.fingerprint().equals(expectedFingerprint)) {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, STALE);
        }
        return verified;
    }

    /** The pack's descriptor; a configured pack without one is unreadable. An unknown pack is refused by the port. */
    private PackDescriptor judged(PackIdentifier pack) {
        return descriptors
                .find(pack)
                .orElseThrow(() ->
                        new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + pack.value() + " has no pack.yaml"));
    }

    /** The pack's own artifact of a kind with an id; an artifact the pack does not hold is not found. */
    private static CharterArtifact held(Pack loaded, PackIdentifier pack, ArtifactKind kind, String id) {
        return loaded.artifacts(kind).stream()
                .filter(candidate -> candidate.localId().equals(id))
                .findFirst()
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.ARTIFACT_NOT_FOUND,
                        "Pack " + pack.value() + " has no " + kind.token() + " " + id + "."));
    }

    /**
     * The configured identifier of the pack the request names. The storage answers a request by the configured name or
     * by the {@code pack_id} the pack declares; the closure's roots are configured identifiers only.
     */
    private PackIdentifier configuredId(PackIdentifier requested, PackDescriptor descriptor) {
        List<ConfiguredPack> all = catalog.configured();
        if (all.stream().anyMatch(candidate -> candidate.id().equals(requested))) {
            return requested;
        }
        return all.stream()
                .map(ConfiguredPack::id)
                .filter(candidate -> descriptors
                        .find(candidate)
                        .map(found -> found.packId().equals(descriptor.packId()))
                        .orElse(false))
                .findFirst()
                // the pack was found a moment ago and is configured no longer: the catalog changed during the read
                .orElseThrow(() -> new CharterRefusal(RefusalCode.STALE_INPUT, STALE));
    }
}
