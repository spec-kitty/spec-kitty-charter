package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The {@code DELETE} of one artifact of a pack (IC-06, FR-005, FR-007, FR-036): the artifact, its companion files, its
 * outgoing edges from {@code registered.graph.yaml} and the items of the pack's presets that name it go, and the rest of
 * the pack stays. It runs entirely under the registry write lock and then the pack's own, in this order, and nothing is
 * written unless every check passes:
 *
 * <ol>
 *   <li>the pack, the kind and the artifact are there ({@code 404}, each its own code), then the {@code If-Match} is met
 *       ({@code 428}, {@code 412});
 *   <li>no other artifact or pack still refers to the artifact, judged against the tree after the removal
 *       ({@link InboundReferences}, {@code 409 artifact_referenced}); then the write gate allows a {@code REMOVE} of it
 *       ({@code 409 governance_policy_violation}); then no graph document the author wrote names it
 *       ({@code 409 registration_conflict}), since a {@code DELETE} never rewrites an author file;
 *   <li>every closure the pack joins still composes without it ({@link RegistrationImpact}, {@code 422}, strict: PR A does
 *       not repair a closure that is already broken, decision DM-01M6TQ67C9ZTNGZB9CY28JS54F);
 *   <li>the pack is swapped whole, so a read sees the old pack or the new one.
 * </ol>
 *
 * <p>A saved selection of the artifact in an activation is not consulted: it reads as stale and is dropped on the next
 * write of the activation.
 */
@Service
public final class ArtifactRemovalService {

    private final PackRepository packs;
    private final PackRemovalStore store;
    private final WritePolicyGate gate;
    private final InboundReferences inbound;
    private final StagedPackViews views;
    private final RegistrationImpact impact;

    /**
     * A service over its ports.
     *
     * @param packs loads the pack
     * @param store locks and swaps
     * @param gate the mutability gate
     * @param inbound the inbound scan
     * @param views the stored packs, read through one session per removal
     * @param impact the impact check
     */
    @Autowired
    public ArtifactRemovalService(
            PackRepository packs,
            PackRemovalStore store,
            WritePolicyGate gate,
            InboundReferences inbound,
            StagedPackViews views,
            RegistrationImpact impact) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.store = Objects.requireNonNull(store, "store");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.inbound = Objects.requireNonNull(inbound, "inbound");
        this.views = Objects.requireNonNull(views, "views");
        this.impact = Objects.requireNonNull(impact, "impact");
    }

    /**
     * Removes one artifact.
     *
     * @param pack the pack's identity
     * @param kindToken the kind's token as the request names it
     * @param artifactId the artifact's id
     * @param ifMatch the {@code If-Match} precondition, required
     * @return the revision after the removal
     * @throws CharterRefusal {@code pack_not_found}, {@code kind_not_found} or the not-found code of the kind (404);
     *     {@code artifact_referenced}, {@code governance_policy_violation} or {@code registration_conflict} (409); a
     *     composition code or a relation code of the impact check (422); {@code pack_unreadable}
     * @throws ai.speckitty.charter.kernel.transport.PreconditionException {@code MISSING} (428) or {@code STALE} (412)
     * @throws RegistryWriteLock.RegistryBusyException when the registry lock was not free in time (503)
     */
    public PackRevision remove(PackIdentifier pack, String kindToken, String artifactId, @Nullable String ifMatch) {
        return remove(pack, kindToken, artifactId, ifMatch, RevisionTrail.NONE);
    }

    /**
     * Removes one artifact and tells the trail which revision it found and which it left.
     *
     * @param pack the pack's identity
     * @param kindToken the kind's token as the request names it
     * @param artifactId the artifact's id
     * @param ifMatch the {@code If-Match} precondition, required
     * @param trail told the stored revision once the {@code If-Match} check has read it, and once more when the removal
     *     changed the pack; a refusal never tells it a move
     * @return the revision after the removal
     * @throws CharterRefusal as for {@link #remove(PackIdentifier, String, String, String)}
     * @throws ai.speckitty.charter.kernel.transport.PreconditionException {@code MISSING} (428) or {@code STALE} (412)
     * @throws RegistryWriteLock.RegistryBusyException when the registry lock was not free in time (503)
     */
    public PackRevision remove(
            PackIdentifier pack, String kindToken, String artifactId, @Nullable String ifMatch, RevisionTrail trail) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(kindToken, "kindToken");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(trail, "trail");
        return store.exclusively(pack, () -> removeUnderLocks(pack, kindToken, artifactId, ifMatch, trail));
    }

    private PackRevision removeUnderLocks(
            PackIdentifier pack, String kindToken, String artifactId, @Nullable String ifMatch, RevisionTrail trail) {
        Pack current = packs.load(pack);
        Urn artifact = requireHeld(current, pack, kindToken, artifactId);
        PackRevision before = RemovalPrecondition.require(store, pack, ifMatch, trail);
        List<Relationship> registered = store.registeredRelationships(pack);
        List<Relationship> outgoing =
                registered.stream().filter(edge -> edge.from().equals(artifact)).toList();
        requireNoReferrer(pack, artifact, outgoing);
        gate.authorize(current, WritePolicyGate.Operation.REMOVE, artifact);
        requireNotAuthored(pack, artifact);
        impact.check(pack, facts -> WriteFacts.withoutArtifact(facts, artifact, outgoing));
        List<Relationship> retained =
                registered.stream().filter(edge -> !outgoing.contains(edge)).toList();
        PackRevision after = store.remove(
                pack, PackRemovalStore.Removal.ofArtifact(artifact, retained, rewritten(current, artifact)));
        trail.moved(before, after);
        return after;
    }

    // ---- 404
    // ----------------------------------------------------------------------------------------------------------

    /** The artifact's URN once the kind is served and the pack holds the artifact; the not-found code of the path otherwise. */
    private static Urn requireHeld(Pack current, PackIdentifier pack, String kindToken, String artifactId) {
        ArtifactKind kind = ArtifactReadService.listedKind(kindToken)
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.KIND_NOT_FOUND, "The service serves no artifact kind " + kindToken + "."));
        CharterRefusal absent = new CharterRefusal(
                ArtifactReadService.absentCode(kind),
                "Pack " + pack.value() + " holds no " + kind.token() + " " + artifactId + ".");
        Urn urn;
        try {
            urn = new Urn(NodeKind.of(kind), artifactId);
        } catch (IllegalArgumentException notAnId) {
            throw absent;
        }
        if (current.artifacts(kind).stream().noneMatch(held -> held.urn().equals(urn))) {
            throw absent;
        }
        return urn;
    }

    // ---- 409
    // ----------------------------------------------------------------------------------------------------------

    /**
     * Before the compose: another artifact or pack that still refers to the artifact is a {@code 409}, not a composition
     * {@code 422}. The scan judges the tree after the removal: the pack's own presets lose the items that name the
     * artifact, and the edges that start at it are gone, so neither counts.
     */
    private void requireNoReferrer(PackIdentifier pack, Urn artifact, List<Relationship> outgoing) {
        StagedPackViews.View view = views.session().stored();
        PackIdentifier configured = store.configuredName(pack);
        PackDescriptor descriptor = view.descriptors().get(configured);
        PackCompositionFacts after =
                WriteFacts.withoutArtifact(view.artifacts().compositionFacts(configured), artifact, outgoing);
        List<InboundReferences.Referrer> referrers = inbound.scan(
                view,
                new InboundReferences.StagedTree(
                        Optional.of(configured), Optional.of(descriptor.name()), descriptor, after),
                Set.of(artifact));
        if (!referrers.isEmpty()) {
            throw InboundReferences.refusal(
                    "The artifact " + artifact + " of the pack " + pack.value() + " is still referred to by "
                            + referrers.size() + " artifact(s), preset(s) or override(s); the first is "
                            + referrers.getFirst().referrer() + " of the pack "
                            + referrers.getFirst().name() + ".",
                    "removal",
                    referrers);
        }
    }

    /** A graph document the author wrote that declares the artifact's node or holds an edge from it is never rewritten. */
    private void requireNotAuthored(PackIdentifier pack, Urn artifact) {
        if (store.authored(pack).names(artifact)) {
            throw new CharterRefusal(
                    RefusalCode.REGISTRATION_CONFLICT,
                    artifact + " is written in the pack's graph documents: register the whole pack to remove it.");
        }
    }

    /** The presets of the pack that name the artifact, each without those items; the others are not rewritten. */
    private static List<Preset> rewritten(Pack current, Urn artifact) {
        List<Preset> changed = new ArrayList<>();
        for (Preset stored : current.presets()) {
            Preset left = WriteFacts.withoutItems(stored, artifact);
            if (!left.equals(stored)) {
                changed.add(left);
            }
        }
        return changed;
    }
}
