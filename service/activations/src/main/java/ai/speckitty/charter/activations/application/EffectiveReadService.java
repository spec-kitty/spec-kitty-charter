package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.application.ActivationService.Read;
import ai.speckitty.charter.activations.domain.ActionContext;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.EffectiveArtifactRead;
import ai.speckitty.charter.activations.domain.EffectiveContext;
import ai.speckitty.charter.activations.domain.EffectiveSet;
import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.EffectiveSet.Summary;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.DenyNotApplied;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.GovernanceFacts;
import ai.speckitty.charter.activations.domain.GraphSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.transport.EntityTags;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The reads of what an activation puts in force: {@code getActivation} with its deny notice, the effective artifact
 * list, the skipped report, the effective graph and the effective action context (IC-09, IC-11, FR-013, FR-017 to
 * FR-021, FR-027).
 *
 * <p>Each read opens one {@link PacksSession}, one verified read of the closure the stored document names (E19), and
 * answers everything from it: the tag, then, only when the {@code If-None-Match} does not match it, the merge and the
 * in-force resolution, once each (NFR-007). The session's read retries a stale input up to three times before it
 * refuses. A refusal of the compose or of the merge is the answer, unchanged, and never a partial one.
 *
 * <p>Every tag is an {@link EffectiveTag}: the strong digest of the representation read, the activation's revision and
 * the closure's own digest ({@link ClosureSnapshot#closureFingerprint()}), so it changes with the document and with a
 * pack of the closure and with nothing else. A page's tag names its kind, state, cursor and page size. The cursor binds
 * the tag of the <em>set</em> it pages (the same kind and state, no cursor and no page size): a cursor of another set
 * is refused ({@link StaleCursorException}), so paging over a recomputed set is stable for one set only.
 */
@Service
public class EffectiveReadService {

    /** The page size part of the tag of a set: a set has no page size. */
    private static final int WHOLE_SET = 0;

    private final ActivationRepository repository;
    private final AvailablePacks packs;

    /**
     * The reads over the register and the packs.
     *
     * @param repository the activation register
     * @param packs the packs, read once per request
     */
    @Autowired
    public EffectiveReadService(ActivationRepository repository, AvailablePacks packs) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    /**
     * Where a page continues.
     *
     * @param position the last artifact of the page before
     * @param setTag the tag of the set that page belonged to
     * @param cursor the cursor as the request carried it; empty for the position a page offers as its next one, which
     *     the wire adapter encodes
     */
    public record After(ArtifactRef position, String setTag, String cursor) {}

    /**
     * What a list request asks.
     *
     * @param kind the kind filter
     * @param state the state filter
     * @param pageSize the page size
     * @param after where the page continues
     */
    public record PageQuery(Optional<ArtifactKind> kind, Optional<State> state, int pageSize, Optional<After> after) {}

    /**
     * An activation as {@code getActivation} answers it.
     *
     * @param stored the stored activation
     * @param denyNotApplied the deny entries the floor or a lock overrides
     */
    public record Viewed(StoredActivation stored, List<ArtifactRef> denyNotApplied) {}

    /**
     * A page of the effective artifacts.
     *
     * @param stored the stored activation
     * @param sourceFingerprint the source fingerprint of the read
     * @param governanceFingerprint the digest of the closure's own packs
     * @param summary the counts of the whole set
     * @param items the page
     * @param next where the next page continues; empty on the last
     * @param query what was asked
     */
    public record Page(
            StoredActivation stored,
            String sourceFingerprint,
            String governanceFingerprint,
            Summary summary,
            List<EffectiveArtifact> items,
            Optional<After> next,
            PageQuery query) {}

    /**
     * The skipped report of an activation.
     *
     * @param stored the stored activation
     * @param sourceFingerprint the source fingerprint of the read
     * @param governanceFingerprint the digest of the closure's own packs
     * @param skipped the report
     */
    public record Report(
            StoredActivation stored,
            String sourceFingerprint,
            String governanceFingerprint,
            EffectiveSkippedReport skipped) {}

    /**
     * The effective graph of an activation.
     *
     * @param stored the stored activation
     * @param sourceFingerprint the source fingerprint of the read
     * @param governanceFingerprint the digest of the closure's own packs
     * @param graph the nodes in force, the edges among them and the layer overrides
     */
    public record GraphRead(
            StoredActivation stored, String sourceFingerprint, String governanceFingerprint, GraphSnapshot graph) {}

    /**
     * The effective context of one action of an activation.
     *
     * @param stored the stored activation
     * @param sourceFingerprint the source fingerprint of the read
     * @param governanceFingerprint the digest of the closure's own packs
     * @param query the mission type, the action and the depth
     * @param context what the action's step names
     * @param missionTypePack the pack that holds the version in force of the mission type, as the effective set names
     *     it; empty when the set does not list the mission type
     */
    public record ContextRead(
            StoredActivation stored,
            String sourceFingerprint,
            String governanceFingerprint,
            EffectiveTag.ContextQuery query,
            EffectiveContext context,
            Optional<String> missionTypePack) {

        /**
         * Checks the fields.
         *
         * @param stored the stored activation
         * @param sourceFingerprint the source fingerprint of the read
         * @param governanceFingerprint the digest of the closure's own packs
         * @param query the mission type, the action and the depth
         * @param context what the action's step names
         * @param missionTypePack the pack that holds the mission type's version in force
         */
        public ContextRead {
            Objects.requireNonNull(missionTypePack, "missionTypePack");
        }

        /**
         * A context whose mission type's holding pack is not named.
         *
         * @param stored the stored activation
         * @param sourceFingerprint the source fingerprint of the read
         * @param governanceFingerprint the digest of the closure's own packs
         * @param query the mission type, the action and the depth
         * @param context what the action's step names
         */
        public ContextRead(
                StoredActivation stored,
                String sourceFingerprint,
                String governanceFingerprint,
                EffectiveTag.ContextQuery query,
                EffectiveContext context) {
            this(stored, sourceFingerprint, governanceFingerprint, query, context, Optional.empty());
        }
    }

    /**
     * The winners of one kind under an activation, as {@link #heldWinners} answers them.
     *
     * @param stored the stored activation
     * @param sourceFingerprint the source fingerprint of the read, which a read of the winners' packs is checked against
     * @param governanceFingerprint the digest of the closure's own packs
     * @param winners the winners, by id in code-point order
     */
    public record HeldWinners(
            StoredActivation stored,
            String sourceFingerprint,
            String governanceFingerprint,
            List<EffectiveHeldWinner> winners) {

        /**
         * Checks and copies the fields.
         *
         * @param stored the stored activation
         * @param sourceFingerprint the source fingerprint
         * @param governanceFingerprint the closure's digest
         * @param winners the winners
         */
        public HeldWinners {
            Objects.requireNonNull(stored, "stored");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
            Objects.requireNonNull(governanceFingerprint, "governanceFingerprint");
            winners = List.copyOf(Objects.requireNonNull(winners, "winners"));
        }
    }

    /**
     * The winner of one artifact of a kind the activation does not resolve, as {@link #heldWinner} answers it.
     *
     * @param stored the stored activation
     * @param sourceFingerprint the source fingerprint of the read, which a read of the winner's pack is checked against
     * @param governanceFingerprint the digest of the closure's own packs
     * @param winner the winner
     */
    public record HeldAnswer(
            StoredActivation stored,
            String sourceFingerprint,
            String governanceFingerprint,
            EffectiveHeldWinner winner) {

        /**
         * Checks the fields.
         *
         * @param stored the stored activation
         * @param sourceFingerprint the source fingerprint
         * @param governanceFingerprint the closure's digest
         * @param winner the winner
         */
        public HeldAnswer {
            Objects.requireNonNull(stored, "stored");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
            Objects.requireNonNull(governanceFingerprint, "governanceFingerprint");
            Objects.requireNonNull(winner, "winner");
        }
    }

    /** A page cursor of another set, which a client holds after the document or a pack of the closure changed. */
    public static final class StaleCursorException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /** A cursor that belongs to another set. */
        public StaleCursorException() {
            super("The page cursor belongs to another set of effective artifacts; read the first page again.");
        }
    }

    /**
     * {@code getActivation}: the stored document, with the deny entries the floor or a lock overrides (B4, FR-027),
     * under the tag of the revision and the closure.
     *
     * @param id the activation's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the activation, or {@code NotModified} when the header matches its tag
     * @throws CharterRefusal with {@code activation_not_found} when nothing is stored under the id, with
     *     {@code pack_unreadable} when a pack the document names is no longer configured, or with the refusal of the
     *     compose or of the merge
     */
    public Read<Viewed> activation(ActivationId id, @Nullable String ifNoneMatch) {
        StoredActivation stored = found(id);
        PacksSession session = open(stored);
        String etag =
                EffectiveTag.activation(stored.revision(), session.snapshot()).token();
        if (EntityTags.noneMatch(ifNoneMatch, etag)) {
            return new Read.NotModified<>(etag);
        }
        InForceAnswer answered = session.inForce(EffectiveSet.queryOf(stored.document()));
        return new Read.Found<>(new Viewed(stored, denied(answered)), etag);
    }

    /**
     * {@code listEffectiveArtifacts}: one page of the artifacts of the merged closure, ordered by kind and then by id.
     *
     * @param id the activation's id
     * @param query what is asked: the filters, the page size and where the page continues
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the page, or {@code NotModified} when the header matches the tag of this representation
     * @throws StaleCursorException when the cursor belongs to another set than the one this request reads
     * @throws CharterRefusal as {@link #activation} does
     */
    public Read<Page> list(ActivationId id, PageQuery query, @Nullable String ifNoneMatch) {
        StoredActivation stored = found(id);
        PacksSession session = open(stored);
        ClosureSnapshot closure = session.snapshot();
        String setTag = EffectiveTag.artifacts(
                        stored.revision(),
                        closure,
                        new EffectiveTag.Query(query.state(), query.kind(), Optional.empty(), WHOLE_SET))
                .token();
        if (query.after().filter(after -> !after.setTag().equals(setTag)).isPresent()) {
            throw new StaleCursorException();
        }
        String etag = EffectiveTag.artifacts(
                        stored.revision(),
                        closure,
                        new EffectiveTag.Query(
                                query.state(), query.kind(), query.after().map(After::cursor), query.pageSize()))
                .token();
        if (EntityTags.noneMatch(ifNoneMatch, etag)) {
            return new Read.NotModified<>(etag);
        }
        EffectiveSet set = effectiveSet(stored, session);
        return new Read.Found<>(pageOf(stored, closure, set, query, setTag), etag);
    }

    /**
     * {@code getActivationSkippedReport}: each artifact not in force once, in the typed bucket of the rule that decided
     * it, and the rules not applied.
     *
     * @param id the activation's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the report, or {@code NotModified} when the header matches its tag
     * @throws CharterRefusal as {@link #activation} does
     */
    public Read<Report> skipped(ActivationId id, @Nullable String ifNoneMatch) {
        StoredActivation stored = found(id);
        PacksSession session = open(stored);
        ClosureSnapshot closure = session.snapshot();
        String etag = EffectiveTag.skipped(stored.revision(), closure).token();
        if (EntityTags.noneMatch(ifNoneMatch, etag)) {
            return new Read.NotModified<>(etag);
        }
        EffectiveSet set = effectiveSet(stored, session);
        return new Read.Found<>(
                new Report(stored, closure.fingerprint(), closure.closureFingerprint(), set.skipped()), etag);
    }

    /**
     * {@code getEffectiveGraph}: the nodes in force (the artifacts in force, and each held step of a mission type in
     * force), the merged edges among them resolved through the layer overrides, and the layer overrides applied.
     *
     * @param id the activation's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the graph, or {@code NotModified} when the header matches its tag
     * @throws CharterRefusal as {@link #activation} does
     */
    public Read<GraphRead> graph(ActivationId id, @Nullable String ifNoneMatch) {
        StoredActivation stored = found(id);
        PacksSession session = open(stored);
        ClosureSnapshot closure = session.snapshot();
        String etag = EffectiveTag.graph(stored.revision(), closure).token();
        if (EntityTags.noneMatch(ifNoneMatch, etag)) {
            return new Read.NotModified<>(etag);
        }
        GraphSnapshot graph = session.graph(EffectiveSet.queryOf(stored.document()));
        return new Read.Found<>(
                new GraphRead(stored, closure.fingerprint(), closure.closureFingerprint(), graph), etag);
    }

    /**
     * {@code getEffectiveActionContext}: ADR-0018's walk from the action's step over the artifacts in force, with the
     * enhancers in force, the suggestions and the skipped report.
     *
     * @param id the activation's id
     * @param query the mission type, the action and the depth
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the context, or {@code NotModified} when the header matches its tag
     * @throws CharterRefusal with {@code mission_type_not_found} or {@code action_not_found} when the merged closure
     *     holds no such mission type or action, judged before {@code If-None-Match} is compared, or as
     *     {@link #activation} does
     */
    public Read<ContextRead> actionContext(
            ActivationId id, EffectiveTag.ContextQuery query, @Nullable String ifNoneMatch) {
        StoredActivation stored = found(id);
        PacksSession session = open(stored);
        ClosureSnapshot closure = session.snapshot();
        session.requireAction(query.missionType(), query.action());
        String etag = EffectiveTag.context(stored.revision(), closure, query).token();
        if (EntityTags.noneMatch(ifNoneMatch, etag)) {
            return new Read.NotModified<>(etag);
        }
        ActionContext walked = session.actionContext(
                query.missionType(), query.action(), query.depth(), EffectiveSet.queryOf(stored.document()));
        EffectiveSet set = effectiveSet(stored, session);
        ArtifactRef missionType = new ArtifactRef(ArtifactKind.MISSION_TYPE, query.missionType());
        EffectiveContext context = EffectiveContext.of(
                set,
                walked,
                missionType,
                NodeRef.of(Urn.action(query.missionType(), query.action())).orElseThrow());
        Optional<String> holder = set.artifacts().stream()
                .filter(artifact -> artifact.ref().equals(missionType))
                .map(EffectiveSet.EffectiveArtifact::packId)
                .findFirst();
        return new Read.Found<>(
                new ContextRead(stored, closure.fingerprint(), closure.closureFingerprint(), query, context, holder),
                etag);
    }

    /**
     * {@code getEffectiveArtifact}: the winner of one artifact under an activation (FR-007): the version the layer merge
     * keeps, the pack and level that hold it, its state and reason as the effective list gives them, and the entity as the
     * winner's pack stores it. A kind token is the kind it spells exactly; the activation is judged first, then the kind.
     *
     * @param id the activation's id
     * @param kindToken the kind's token, as the request names it
     * @param artifactId the artifact's id, exactly as declared
     * @param depth the number of {@code suggests} hops of a profile's governance, at least 0
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the winner, or {@code NotModified} when the header matches the tag of this representation
     * @throws CharterRefusal with {@code activation_not_found}, then {@code kind_not_found} when the token names a kind
     *     the packs slice does not list, or a listed kind the activation does not resolve (an asset, research R3), naming
     *     its literal read, or as {@link #artifact(ActivationId, ArtifactKind, String, int, String)} does
     */
    public Read<EffectiveArtifactAnswer> artifact(
            ActivationId id, String kindToken, String artifactId, int depth, @Nullable String ifNoneMatch) {
        StoredActivation stored = found(id);
        ArtifactKind kind = ArtifactKind.fromToken(kindToken)
                .filter(packs.listedKinds()::contains)
                .orElseThrow(() -> notServed(kindToken));
        requireActivatable(id, kind, artifactId);
        return artifactOf(stored, kind, artifactId, depth, ifNoneMatch, EffectiveTag.Read.ARTIFACT);
    }

    /**
     * {@code getEffectiveArtifact}, for a caller that holds the kind already (the profiles slice's route, the mission
     * type route): the same read as the token form, judged in the same order.
     *
     * <p>One {@link PacksSession} answers it: the merge decides whether a pack of the closure holds the artifact, before
     * any {@code If-None-Match} is compared, so a name no pack holds is a {@code 404} whatever the header says; the tag
     * is the {@link EffectiveTag} of the read, which names the kind, the id and the depth and, for an agent profile, its
     * own read; and only a request whose header does not match asks the session for the entity and the in-force
     * resolution, once each. An agent profile's answer carries its governance with the state of each entry, under the
     * tag of its own read ({@link EffectiveTag.Read#PROFILE}); a skill's answer is the read of the skill route, under the
     * tag of its own read too ({@link EffectiveTag.Read#SKILL}), so neither shares a tag with the generic route.
     *
     * @param id the activation's id
     * @param kind the artifact's kind
     * @param artifactId the artifact's id, exactly as declared
     * @param depth the number of {@code suggests} hops of a profile's governance, at least 0
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the winner, or {@code NotModified} when the header matches the tag of this representation
     * @throws CharterRefusal with {@code activation_not_found}; {@code kind_not_found} when the packs slice does not list
     *     the kind, or lists one the activation does not resolve (an asset), naming its literal read; {@code pack_unreadable} when a pack the activation names is no longer configured, or the winner's
     *     pack cannot read the artifact, or as the merge refuses; {@code artifact_not_found} when no pack of the closure
     *     holds the artifact; {@code stale_input} when the packs changed since the session was verified
     */
    public Read<EffectiveArtifactAnswer> artifact(
            ActivationId id, ArtifactKind kind, String artifactId, int depth, @Nullable String ifNoneMatch) {
        Objects.requireNonNull(kind, "kind");
        StoredActivation stored = found(id);
        if (!packs.listedKinds().contains(kind)) {
            throw notServed(kind.token());
        }
        requireActivatable(id, kind, artifactId);
        EffectiveTag.Read route = switch (kind) {
            case AGENT_PROFILE -> EffectiveTag.Read.PROFILE;
            case SKILL -> EffectiveTag.Read.SKILL;
            default -> EffectiveTag.Read.ARTIFACT;
        };
        return artifactOf(stored, kind, artifactId, depth, ifNoneMatch, route);
    }

    /**
     * The winners of one kind under an activation, in id order (research R2, R3): {@code listEffectiveSkills} and
     * {@code listEffectiveAssets}. Each is the version the layer merge keeps across the levels of the closure, with the
     * pack and level that hold it and its policy, and no state; a skill's state is its own read's, and an asset has
     * none. The whole set is answered, unpaged, from one {@link PacksSession}, with no in-force resolution; what each
     * winner's pack stores is the packs slice's to read, against the source fingerprint of the answer.
     *
     * @param id the activation's id
     * @param kind {@code skill} or {@code asset}
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the winners, or {@code NotModified} when the header matches the tag of the list
     * @throws IllegalArgumentException when the contract lists no winners of the kind under an activation
     * @throws CharterRefusal with {@code activation_not_found}, or as {@link #activation} does
     */
    public Read<HeldWinners> heldWinners(ActivationId id, ArtifactKind kind, @Nullable String ifNoneMatch) {
        Objects.requireNonNull(kind, "kind");
        EffectiveTag.Read read = switch (kind) {
            case SKILL -> EffectiveTag.Read.SKILLS;
            case ASSET -> EffectiveTag.Read.ASSETS;
            default ->
                throw new IllegalArgumentException("No read lists the winners of " + kind.token()
                        + " under an activation; listEffectiveArtifacts lists them.");
        };
        StoredActivation stored = found(id);
        PacksSession session = open(stored);
        ClosureSnapshot closure = session.snapshot();
        String etag =
                EffectiveTag.winners(read, kind, stored.revision(), closure).token();
        if (EntityTags.noneMatch(ifNoneMatch, etag)) {
            return new Read.NotModified<>(etag);
        }
        return new Read.Found<>(
                new HeldWinners(stored, closure.fingerprint(), closure.closureFingerprint(), session.heldWinners(kind)),
                etag);
    }

    /**
     * {@code getEffectiveAsset}: the winner of one artifact of a kind the activation does not resolve (an asset, research
     * R2, operator ruling {@code DM-01M78S3RNPZ5ZFV5AR6H1X6HV1}): the version the layer merge keeps, the pack and level
     * that hold it and its policy, and no state, reason or required, which would be invented. The activation is judged
     * first, then whether a pack of the closure holds the artifact, both before any {@code If-None-Match}.
     *
     * @param id the activation's id
     * @param kind a kind the activation does not resolve
     * @param artifactId the artifact's id, exactly as declared
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the winner, or {@code NotModified} when the header matches the tag of this representation
     * @throws IllegalArgumentException when the kind is one the activation resolves, which {@link #artifact} reads
     * @throws CharterRefusal with {@code activation_not_found}; {@code artifact_not_found} when no pack of the closure
     *     holds the artifact; or as {@link #activation} does
     */
    public Read<HeldAnswer> heldWinner(
            ActivationId id, ArtifactKind kind, String artifactId, @Nullable String ifNoneMatch) {
        return heldOf(id, kind, artifactId, ifNoneMatch, EffectiveTag.Read.ASSET);
    }

    /**
     * {@code getEffectiveAssetPayload}: the winner of one asset, as {@link #heldWinner} judges it, under the tag of the
     * payload read (research R15). The bytes are the packs slice's to read from the winner's pack, against the source
     * fingerprint of this answer, so they are of the inputs this read judged or are refused {@code stale_input}.
     *
     * @param id the activation's id
     * @param assetId the asset's id, exactly as declared
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the winner, or {@code NotModified} when the header matches the tag of the payload
     * @throws CharterRefusal as {@link #heldWinner} does
     */
    public Read<HeldAnswer> heldPayload(ActivationId id, String assetId, @Nullable String ifNoneMatch) {
        return heldOf(id, ArtifactKind.ASSET, assetId, ifNoneMatch, EffectiveTag.Read.ASSET_PAYLOAD);
    }

    private Read<HeldAnswer> heldOf(
            ActivationId id,
            ArtifactKind kind,
            String artifactId,
            @Nullable String ifNoneMatch,
            EffectiveTag.Read read) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(artifactId, "artifactId");
        if (kind.activatable()) {
            throw new IllegalArgumentException(
                    "The activation resolves " + kind.token() + "; its winner is read with its state by artifact.");
        }
        StoredActivation stored = found(id);
        PacksSession session = open(stored);
        ClosureSnapshot closure = session.snapshot();
        EffectiveHeldWinner winner = session.heldWinner(kind, artifactId)
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.ARTIFACT_NOT_FOUND,
                        "No pack of the activation's closure holds the " + kind.token() + " " + artifactId + "."));
        String etag = EffectiveTag.winner(
                        read, stored.revision(), closure, new EffectiveTag.ArtifactQuery(kind, artifactId, 0))
                .token();
        if (EntityTags.noneMatch(ifNoneMatch, etag)) {
            return new Read.NotModified<>(etag);
        }
        return new Read.Found<>(
                new HeldAnswer(stored, closure.fingerprint(), closure.closureFingerprint(), winner), etag);
    }

    /**
     * The gate of the templated read of one artifact under an activation (T008, FR-011, research R3): a listed kind the
     * activation does not resolve has no state to answer there, so it is {@code kind_not_found}, and the refusal names
     * the literal read of the kind. It is judged after the activation and before the packs are read.
     */
    private static void requireActivatable(ActivationId id, ArtifactKind kind, String artifactId) {
        if (!kind.activatable()) {
            throw new CharterRefusal(
                    RefusalCode.KIND_NOT_FOUND,
                    "The templated effective read renders no " + kind.token() + "; read it at /activations/"
                            + id.value() + "/artifacts/" + kind.token() + "/" + artifactId + ".");
        }
    }

    /**
     * The winner of one mission type under an activation (#99, FR-007), for the missions slice's reads of the type, its
     * steps and its skeletons: the version the layer merge keeps, the pack that holds it, and the merged closure's step
     * contracts bound to the type, each the winner of its own id, all from one {@link PacksSession}. The type is
     * answered whatever its state, as {@link #artifact} answers it; what the holding pack stores beside it (its skeletons
     * and step files) is the packs slice's to read, and the source fingerprint is what a caller checks that read
     * against. The activation is judged first, then the type.
     *
     * @param id the activation's id
     * @param missionTypeId the mission type's id, exactly as declared
     * @return the winning type, its pack, the step contracts bound to it and the source fingerprint of the read
     * @throws CharterRefusal with {@code activation_not_found}; {@code pack_unreadable} when a pack the activation names
     *     is no longer configured or a winner's pack cannot read it, or as the merge or the in-force resolution refuses;
     *     {@code mission_type_not_found} when no pack of the closure holds the type; {@code stale_input} when the packs
     *     changed since the session was verified
     */
    public EffectiveMissionTypeWinner missionTypeWinner(ActivationId id, String missionTypeId) {
        Objects.requireNonNull(missionTypeId, "missionTypeId");
        StoredActivation stored = found(id);
        PacksSession session = open(stored);
        MergedSnapshot merged = session.merged();
        ArtifactRef type = new ArtifactRef(ArtifactKind.MISSION_TYPE, missionTypeId);
        if (!merged.winners().containsKey(type)) {
            throw new CharterRefusal(
                    RefusalCode.MISSION_TYPE_NOT_FOUND,
                    "No pack of the activation's closure holds the mission type " + missionTypeId + ".");
        }
        InForceQuery query = EffectiveSet.queryOf(stored.document());
        String read = "The read of the mission type " + missionTypeId + " under activation " + id.value();
        EffectiveArtifactRead winner = winnerOf(session, type, query, read);
        List<CharterArtifact> contracts = merged.bindings().entrySet().stream()
                .filter(binding -> binding.getValue().equals(type))
                .map(Map.Entry::getKey)
                .filter(bound -> bound.kind() == ArtifactKind.MISSION_STEP_CONTRACT)
                .sorted()
                .map(bound -> winnerOf(session, bound, query, read).artifact())
                .toList();
        return new EffectiveMissionTypeWinner(
                winner.packId(),
                winner.artifact(),
                contracts,
                session.snapshot().fingerprint());
    }

    /**
     * The winner of one step contract under an activation (#99, D11), for the missions slice's literal read of it: the
     * version the layer merge keeps and the pack that holds it, the winner of the mission type the merge binds it to when
     * the closure holds that type, the artifacts of a listed kind the closure holds, and the source fingerprint, all
     * from one {@link PacksSession}. The contract is answered whatever its state, as {@link #artifact} answers it. The
     * activation is judged first, then the contract.
     *
     * @param id the activation's id
     * @param stepContractId the step contract's id, exactly as declared
     * @return the winning contract, its pack, its bound type, the held artifacts and the source fingerprint
     * @throws CharterRefusal with {@code activation_not_found}; {@code pack_unreadable} when a pack the activation names
     *     is no longer configured or a winner's pack cannot read it, or as the merge or the in-force resolution refuses;
     *     {@code mission_step_contract_not_found} when no pack of the closure holds the contract; {@code stale_input}
     *     when the packs changed since the session was verified
     */
    public EffectiveStepContractWinner stepContractWinner(ActivationId id, String stepContractId) {
        Objects.requireNonNull(stepContractId, "stepContractId");
        StoredActivation stored = found(id);
        PacksSession session = open(stored);
        MergedSnapshot merged = session.merged();
        ArtifactRef contract = new ArtifactRef(ArtifactKind.MISSION_STEP_CONTRACT, stepContractId);
        if (!merged.winners().containsKey(contract)) {
            throw new CharterRefusal(
                    RefusalCode.MISSION_STEP_CONTRACT_NOT_FOUND,
                    "No pack of the activation's closure holds the mission step contract " + stepContractId + ".");
        }
        InForceQuery query = EffectiveSet.queryOf(stored.document());
        String read = "The read of the step contract " + stepContractId + " under activation " + id.value();
        EffectiveArtifactRead winner = winnerOf(session, contract, query, read);
        Optional<CharterArtifact> type = Optional.ofNullable(merged.bindings().get(contract))
                .filter(merged.winners()::containsKey)
                .map(bound -> winnerOf(session, bound, query, read).artifact());
        List<ArtifactKind> listed = packs.listedKinds();
        Set<ArtifactRef> held = merged.winners().keySet().stream()
                .filter(ref -> listed.contains(ref.kind()))
                .collect(Collectors.toUnmodifiableSet());
        return new EffectiveStepContractWinner(
                winner.packId(),
                winner.artifact(),
                type,
                held,
                session.snapshot().fingerprint());
    }

    /**
     * The kinds whose artifacts the packs slice lists, which are the kinds {@link #artifact} serves and the kinds an
     * entry of a profile's governance can be linked by (a pack-scoped read of it). The packs slice is asked, so a kind it
     * opens later is served here without a change.
     *
     * @return the listed kinds
     */
    public List<ArtifactKind> listedKinds() {
        return packs.listedKinds();
    }

    /**
     * The one read behind every route. {@code route} is {@link EffectiveTag.Read#PROFILE} for the profiles slice's route,
     * whose body carries the governance, and {@link EffectiveTag.Read#SKILL} for the skill route, whose body carries the
     * skill; the generic route names {@link EffectiveTag.Read#ARTIFACT} for every kind, agent profile and skill
     * included, because its body never renders either, so no two URLs share a tag.
     */
    private Read<EffectiveArtifactAnswer> artifactOf(
            StoredActivation stored,
            ArtifactKind kind,
            String artifactId,
            int depth,
            @Nullable String ifNoneMatch,
            EffectiveTag.Read route) {
        boolean profileRoute = route == EffectiveTag.Read.PROFILE;
        PacksSession session = open(stored);
        ClosureSnapshot closure = session.snapshot();
        ArtifactRef ref = new ArtifactRef(kind, artifactId);
        MergedSnapshot merged = session.merged();
        if (!merged.winners().containsKey(ref)) {
            throw new CharterRefusal(
                    RefusalCode.ARTIFACT_NOT_FOUND,
                    "No pack of the activation's closure holds the " + kind.token() + " " + artifactId + ".");
        }
        EffectiveTag.ArtifactQuery asked = new EffectiveTag.ArtifactQuery(kind, artifactId, depth);
        EffectiveTag tag = switch (route) {
            case PROFILE -> EffectiveTag.profile(stored.revision(), closure, asked);
            case ARTIFACT -> EffectiveTag.artifact(stored.revision(), closure, asked);
            default -> EffectiveTag.winner(route, stored.revision(), closure, asked);
        };
        String etag = tag.token();
        if (EntityTags.noneMatch(ifNoneMatch, etag)) {
            return new Read.NotModified<>(etag);
        }
        InForceQuery query = EffectiveSet.queryOf(stored.document());
        EffectiveArtifactRead read = session.effectiveArtifact(kind, artifactId, depth, query)
                .orElseThrow(() -> new IllegalStateException(
                        "The merged closure holds " + ref.urn() + ", and the session read no winner of it."));
        EffectiveSet set = EffectiveSet.of(stored.document(), closure, merged, session.inForce(query));
        Map<ArtifactRef, EffectiveArtifact> byRef = new HashMap<>();
        set.artifacts().forEach(artifact -> byRef.put(artifact.ref(), artifact));
        EffectiveArtifactAnswer answer = new EffectiveArtifactAnswer(
                stored,
                closure.fingerprint(),
                closure.closureFingerprint(),
                inSet(byRef, ref),
                read.artifact(),
                profileRoute
                        ? read.governance().map(governance -> governed(governance, byRef, set))
                        : Optional.empty());
        return new Read.Found<>(answer, etag);
    }

    /** A profile's governance with the state of each artifact it lists, from the one effective set of the request. */
    private static EffectiveArtifactAnswer.Governance governed(
            GovernanceFacts governance, Map<ArtifactRef, EffectiveArtifact> byRef, EffectiveSet set) {
        List<EffectiveArtifactAnswer.Entry> entries = governance.entries().stream()
                .map(entry -> new EffectiveArtifactAnswer.Entry(
                        inSet(byRef, entry.ref()),
                        entry.step(),
                        entry.relation(),
                        entry.via(),
                        entry.hops(),
                        new EffectiveArtifactAnswer.EdgeWords(entry.when(), entry.reason())))
                .toList();
        List<EffectiveArtifactAnswer.Suggested> suggested = governance.suggestionCandidates().stream()
                .flatMap(candidate -> set.suggested(candidate.ref(), candidate.when(), candidate.reason()).stream()
                        .map(artifact -> new EffectiveArtifactAnswer.Suggested(
                                artifact,
                                candidate.via(),
                                candidate.hops(),
                                new EffectiveArtifactAnswer.EdgeWords(candidate.when(), candidate.reason()))))
                .toList();
        return new EffectiveArtifactAnswer.Governance(entries, suggested);
    }

    private static EffectiveArtifact inSet(Map<ArtifactRef, EffectiveArtifact> byRef, ArtifactRef ref) {
        EffectiveArtifact artifact = byRef.get(ref);
        if (artifact == null) {
            throw new IllegalStateException("The read lists " + ref.urn() + ", which the effective set does not hold.");
        }
        return artifact;
    }

    private static CharterRefusal notServed(String kindToken) {
        return new CharterRefusal(RefusalCode.KIND_NOT_FOUND, "The service serves no artifact kind " + kindToken + ".");
    }

    /** The page of the set the query asks for: filtered, strictly after the cursor's position, at most a page size. */
    private static Page pageOf(
            StoredActivation stored, ClosureSnapshot closure, EffectiveSet set, PageQuery query, String setTag) {
        Predicate<EffectiveArtifact> kind =
                item -> query.kind().map(wanted -> item.kind() == wanted).orElse(true);
        Predicate<EffectiveArtifact> state =
                item -> query.state().map(wanted -> item.state() == wanted).orElse(true);
        Predicate<EffectiveArtifact> after = item -> query.after()
                .map(from -> item.ref().compareTo(from.position()) > 0)
                .orElse(true);
        List<EffectiveArtifact> matching = set.artifacts().stream()
                .filter(kind)
                .filter(state)
                .filter(after)
                .toList();
        List<EffectiveArtifact> items =
                matching.stream().limit(query.pageSize()).toList();
        Optional<After> next = matching.size() > items.size()
                ? Optional.of(new After(items.getLast().ref(), setTag, ""))
                : Optional.empty();
        return new Page(stored, closure.fingerprint(), closure.closureFingerprint(), set.summary(), items, next, query);
    }

    /** The effective set of the stored document: the merge and the in-force resolution, asked once of the session. */
    private static EffectiveSet effectiveSet(StoredActivation stored, PacksSession session) {
        MergedSnapshot merged = session.merged();
        InForceAnswer answered = session.inForce(EffectiveSet.queryOf(stored.document()));
        return EffectiveSet.of(stored.document(), session.snapshot(), merged, answered);
    }

    /** The deny entries not applied, as artifacts, each once, in order. */
    private static List<ArtifactRef> denied(InForceAnswer answered) {
        return answered.denyNotApplied().stream()
                .map(DenyNotApplied::ref)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * The winner of an artifact the merged closure holds, read from the pack that holds it, whatever its state. A
     * winner the session cannot read is a broken invariant, never a refusal, and the fault names {@code read}, the read
     * that lacks it.
     */
    private static EffectiveArtifactRead winnerOf(
            PacksSession session, ArtifactRef ref, InForceQuery query, String read) {
        return session.effectiveArtifact(ref.kind(), ref.id(), 0, query)
                .orElseThrow(() -> new IllegalStateException(
                        read + ": the merged closure holds " + ref.urn() + ", and the session read no winner of it."));
    }

    /** One verified read of the closure the document names, refused when a pack it names is no longer configured. */
    private PacksSession open(StoredActivation stored) {
        PacksSession session = packs.session(stored.document()
                .packs()
                .map(list -> list.stream().map(PackRef::packId).toList()));
        if (!session.snapshot().resolved()) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    "The activation builds on "
                            + String.join(", ", session.snapshot().unknownPacks())
                            + ", which no pack is configured as any more.");
        }
        return session;
    }

    private StoredActivation found(ActivationId id) {
        return repository
                .find(id)
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.ACTIVATION_NOT_FOUND, "No activation is stored as '" + id + "'."));
    }
}
