package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.application.ActivationService.Read;
import ai.speckitty.charter.activations.domain.ActionContext;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.EffectiveArtifactRead;
import ai.speckitty.charter.activations.domain.EffectiveContext;
import ai.speckitty.charter.activations.domain.EffectivePrompt;
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
import ai.speckitty.charter.activations.domain.ProfileResolution;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.kernel.domain.ModelBearer;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.TermHolder;
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
 * list, the skipped report, the effective graph, the effective action context (IC-09, IC-11, FR-013, FR-017 to FR-021,
 * FR-027) and the effective prompt of a step (mission effective-step-and-action-context, FR-003 to FR-005).
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

    /** A profile that declares no model and no effort: what any other artifact gives the recommendation. */
    private static final ModelBearer NO_MODEL = new ModelBearer() {
        @Override
        public Optional<String> model() {
            return Optional.empty();
        }

        @Override
        public Optional<String> effort() {
            return Optional.empty();
        }
    };

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
     * @param stepPack the pack of the step's place ({@link PacksSession#stepPlace}; research R8, A7): the winner of the
     *     mission type when its version declares the step, else the merged winner of the action whose version does;
     *     empty when the effective set does not list the mission type or no version at either place declares the step
     * @param missionTypeInForce whether the activation puts the mission type in force, so that the effective prompt of
     *     the action answers (FR-007: the context links it only then)
     */
    public record ContextRead(
            StoredActivation stored,
            String sourceFingerprint,
            String governanceFingerprint,
            EffectiveTag.ContextQuery query,
            EffectiveContext context,
            Optional<String> stepPack,
            boolean missionTypeInForce) {

        /**
         * Checks the fields.
         *
         * @param stored the stored activation
         * @param sourceFingerprint the source fingerprint of the read
         * @param governanceFingerprint the digest of the closure's own packs
         * @param query the mission type, the action and the depth
         * @param context what the action's step names
         * @param stepPack the pack of the step's place
         * @param missionTypeInForce whether the activation puts the mission type in force
         */
        public ContextRead {
            Objects.requireNonNull(stepPack, "stepPack");
        }

        /**
         * A context whose mission type's holding pack is not named, of a mission type not in force.
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
            this(stored, sourceFingerprint, governanceFingerprint, query, context, Optional.empty(), false);
        }
    }

    /**
     * The effective prompt of one step of an activation.
     *
     * @param stored the stored activation
     * @param sourceFingerprint the source fingerprint of the read
     * @param governanceFingerprint the digest of the closure's own packs
     * @param query the mission type and the action
     * @param prompt the prompt in force, its slots and their fragments in force
     */
    public record PromptRead(
            StoredActivation stored,
            String sourceFingerprint,
            String governanceFingerprint,
            EffectiveTag.PromptQuery query,
            EffectivePrompt prompt) {

        /**
         * Checks the fields.
         *
         * @param stored the stored activation
         * @param sourceFingerprint the source fingerprint of the read
         * @param governanceFingerprint the digest of the closure's own packs
         * @param query the mission type and the action
         * @param prompt the effective prompt
         */
        public PromptRead {
            Objects.requireNonNull(stored, "stored");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
            Objects.requireNonNull(governanceFingerprint, "governanceFingerprint");
            Objects.requireNonNull(query, "query");
            Objects.requireNonNull(prompt, "prompt");
        }
    }

    /**
     * What a profile selection under an activation chooses from, judged and tagged (selectAgentProfile, ruling D1 of
     * slice G). The selection and the {@code If-None-Match} are the profiles slice's: it refuses
     * {@code no_profile_matched} before it compares the tag, so no refusal is a {@code 304}.
     *
     * @param stored the stored activation
     * @param sourceFingerprint the source fingerprint of the read
     * @param governanceFingerprint the digest of the closure's own packs
     * @param role the role selection reads: the asked role, or the role the asked step names; empty for a step that
     *     names none, which any role may act
     * @param candidates the profiles in force, the pin and the fallback, with no conflict among them
     * @param etag the tag of the representation, unquoted
     */
    public record SelectionRead(
            StoredActivation stored,
            String sourceFingerprint,
            String governanceFingerprint,
            Optional<String> role,
            ProfileCandidates candidates,
            String etag) {

        /**
         * Checks the fields.
         *
         * @param stored the stored activation
         * @param sourceFingerprint the source fingerprint
         * @param governanceFingerprint the closure's digest
         * @param role the role
         * @param candidates the candidates
         * @param etag the tag
         */
        public SelectionRead {
            Objects.requireNonNull(stored, "stored");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
            Objects.requireNonNull(governanceFingerprint, "governanceFingerprint");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(candidates, "candidates");
            Objects.requireNonNull(etag, "etag");
        }
    }

    /**
     * The activation's half of {@code getModelRecommendation} (ADR-0080 decisions 7 to 13), as
     * {@link #modelRecommendation} answers it: the catalog in force and what the routing slice ranks it beside.
     *
     * @param stored the stored activation
     * @param sourceFingerprint the source fingerprint of the read
     * @param governanceFingerprint the digest of the closure's own packs
     * @param catalogWinner the catalog in force, with the pack and level that hold it
     * @param catalog the catalog as its winning pack declares it
     * @param profileModel the effective profile's model, when it declares one
     * @param profileEffort the effective profile's effort, when it declares one
     * @param stepOffer the asked step's model tier at its place, when a step was asked and declares one
     * @param etag the tag of the representation, unquoted
     */
    public record RecommendationRead(
            StoredActivation stored,
            String sourceFingerprint,
            String governanceFingerprint,
            EffectiveHeldWinner catalogWinner,
            CharterArtifact catalog,
            Optional<String> profileModel,
            Optional<String> profileEffort,
            Optional<CostTier> stepOffer,
            String etag) {

        /**
         * Checks the fields.
         *
         * @param stored the stored activation
         * @param sourceFingerprint the source fingerprint
         * @param governanceFingerprint the closure's digest
         * @param catalogWinner the catalog's winner
         * @param catalog the catalog
         * @param profileModel the profile's model
         * @param profileEffort the profile's effort
         * @param stepOffer the step's tier
         * @param etag the tag
         */
        public RecommendationRead {
            Objects.requireNonNull(stored, "stored");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
            Objects.requireNonNull(governanceFingerprint, "governanceFingerprint");
            Objects.requireNonNull(catalogWinner, "catalogWinner");
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(profileModel, "profileModel");
            Objects.requireNonNull(profileEffort, "profileEffort");
            Objects.requireNonNull(stepOffer, "stepOffer");
            Objects.requireNonNull(etag, "etag");
        }
    }

    /**
     * The place of a step under an activation.
     *
     * @param packId the configured id of the pack that holds the merged winner of the step's action
     * @param sourceFingerprint the source fingerprint of the read
     */
    public record StepPlace(String packId, String sourceFingerprint) {

        /**
         * Checks the fields.
         *
         * @param packId the place's pack
         * @param sourceFingerprint the source fingerprint
         */
        public StepPlace {
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
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

    /**
     * The winner of one artifact of any listed kind and what else the closure holds, as {@link #listedWinner} answers
     * them: the referrer of {@code listEffectiveArtifactReferences} (research R10).
     *
     * @param stored the stored activation
     * @param sourceFingerprint the source fingerprint of the read, which the read of the winner's pack is checked against
     * @param governanceFingerprint the digest of the closure's own packs
     * @param winner the winner
     * @param held every artifact of a listed kind the closure holds, as the layer merge keeps it
     */
    public record ListedWinner(
            StoredActivation stored,
            String sourceFingerprint,
            String governanceFingerprint,
            EffectiveHeldWinner winner,
            Set<ArtifactRef> held) {

        /**
         * Checks and copies the fields.
         *
         * @param stored the stored activation
         * @param sourceFingerprint the source fingerprint
         * @param governanceFingerprint the closure's digest
         * @param winner the winner
         * @param held the held artifacts
         */
        public ListedWinner {
            Objects.requireNonNull(stored, "stored");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
            Objects.requireNonNull(governanceFingerprint, "governanceFingerprint");
            Objects.requireNonNull(winner, "winner");
            held = Set.copyOf(Objects.requireNonNull(held, "held"));
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
                NodeRef.of(Urn.action(query.missionType(), query.action())).orElseThrow(),
                session.tensions(EffectiveSet.queryOf(stored.document())));
        Optional<EffectiveSet.EffectiveArtifact> listed = set.artifacts().stream()
                .filter(artifact -> artifact.ref().equals(missionType))
                .findFirst();
        return new Read.Found<>(
                new ContextRead(
                        stored,
                        closure.fingerprint(),
                        closure.closureFingerprint(),
                        query,
                        context,
                        listed.flatMap(artifact -> session.stepPlace(query.missionType(), query.action())),
                        listed.filter(artifact -> artifact.state() == State.ACTIVE)
                                .isPresent()),
                etag);
    }

    /**
     * {@code getEffectiveStepPrompt}: the prompt in force of one step, with the fragments in force of each slot it
     * declares (FR-003 to FR-005; ADR-0066 decisions 4, 5 and 9).
     *
     * <p>The order is the contract's (research R10): the activation, then the names of the mission type and the action on
     * the merged closure, then whether the activation puts the mission type in force, then the closure's own refusals
     * (the in-force resolution and the slot check), and only then the tag and the {@code If-None-Match}. A conditional
     * read therefore pays for the merge and the in-force resolution before it can answer {@code 304}; the session keeps
     * both for the request, so the read that does not match pays for nothing twice (squad A10, recorded in R10).
     *
     * @param id the activation's id
     * @param query the mission type and the action
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the prompt, or {@code NotModified} when the header matches its tag
     * @throws CharterRefusal with {@code activation_not_found}; {@code mission_type_not_found} or
     *     {@code action_not_found} when the merged closure holds no such mission type or action, whether or not the
     *     mission type is in force; {@code mission_type_not_in_force} when the closure holds it and the activation does
     *     not put it in force; then as {@link #activation} does, {@code prompt_slot_missing} included, each before
     *     {@code If-None-Match} is compared
     */
    public Read<PromptRead> prompt(ActivationId id, EffectiveTag.PromptQuery query, @Nullable String ifNoneMatch) {
        StoredActivation stored = found(id);
        PacksSession session = open(stored);
        ClosureSnapshot closure = session.snapshot();
        session.requireAction(query.missionType(), query.action());
        InForceQuery asked = EffectiveSet.queryOf(stored.document());
        if (!session.isInForce(new ArtifactRef(ArtifactKind.MISSION_TYPE, query.missionType()), asked)) {
            throw new CharterRefusal(
                    RefusalCode.MISSION_TYPE_NOT_IN_FORCE,
                    "The activation " + id.value() + " does not put the mission type " + query.missionType()
                            + " in force, so its step " + query.action() + " has no prompt to render; select it.");
        }
        // The composing read's refusals (the in-force resolution's and the slot check's) come before the tag.
        session.inForce(asked);
        String etag = EffectiveTag.prompt(stored.revision(), closure, query).token();
        if (EntityTags.noneMatch(ifNoneMatch, etag)) {
            return new Read.NotModified<>(etag);
        }
        EffectivePrompt prompt = session.prompt(query.missionType(), query.action(), asked);
        return new Read.Found<>(
                new PromptRead(stored, closure.fingerprint(), closure.closureFingerprint(), query, prompt), etag);
    }

    /**
     * {@code selectAgentProfile}, the activation's half (ruling D1 of slice G, research R10's order): the candidates of
     * one role, read from one {@link PacksSession} under the stored document, with the pin and the fallback in force,
     * and the tag of the representation. The order follows the action context and the prompt reads: the activation,
     * then the closure's own refusals (the merge and the in-force resolution), then the conflicts, the fallback's before
     * the pin's, whether or not a profile would match, and only then the tag. The profiles slice then selects, refuses
     * {@code no_profile_matched}, and compares {@code If-None-Match}.
     *
     * @param id the activation's id
     * @param query the role and the asked context
     * @return the candidates, conflict-free, and the tag
     * @throws CharterRefusal with {@code activation_not_found}; as {@link #activation} does; then
     *     {@code fallback_profile_conflict} when two packs of the closure, neither derived from the other, declare
     *     different fallbacks, or {@code role_pin_conflict} when two packs pin the role to different profiles, each
     *     naming the packs; {@code stale_input} when the packs changed since the session was verified
     */
    public SelectionRead profileSelection(ActivationId id, EffectiveTag.SelectionQuery query) {
        Objects.requireNonNull(query, "query");
        StoredActivation stored = found(id);
        PacksSession session = open(stored);
        ClosureSnapshot closure = session.snapshot();
        return selectionOf(
                id,
                stored,
                session,
                Optional.of(query.role()),
                EffectiveTag.selection(stored.revision(), closure, query));
    }

    /**
     * {@code selectAgentProfileForStep}, the activation's half (FR-012): the step is judged as the action context and the
     * prompt read judge it, its role is read at the step's place, and the candidates of that role are read and judged as
     * {@link #profileSelection} reads and judges them. A step names a role and never a profile, and a substep never
     * delegates to one (slice F), so the role is the only source of the step's profile. A step that names no role reads
     * no pin: any role may act, so the profiles slice answers the fallback in force.
     *
     * @param id the activation's id
     * @param query the step and the asked context
     * @return the step's role, the candidates, conflict-free, and the tag
     * @throws CharterRefusal with {@code activation_not_found}; {@code mission_type_not_found} or
     *     {@code action_not_found} when the merged closure holds no such mission type or step, and
     *     {@code action_not_found} when it holds the step and no version of the mission type at its place declares it;
     *     then as {@link #profileSelection} does
     */
    public SelectionRead stepProfileSelection(ActivationId id, EffectiveTag.StepSelectionQuery query) {
        Objects.requireNonNull(query, "query");
        StoredActivation stored = found(id);
        PacksSession session = open(stored);
        ClosureSnapshot closure = session.snapshot();
        session.requireAction(query.missionType(), query.action());
        if (session.stepPlace(query.missionType(), query.action()).isEmpty()) {
            throw new CharterRefusal(
                    RefusalCode.ACTION_NOT_FOUND,
                    "No version of the mission type " + query.missionType()
                            + " in the activation's closure declares the step " + query.action() + ".");
        }
        return selectionOf(
                id,
                stored,
                session,
                session.stepRole(query.missionType(), query.action()),
                EffectiveTag.stepSelection(stored.revision(), closure, query));
    }

    /** The candidates of a role from one session, judged for conflicts, then tagged (research R10's order). */
    private static SelectionRead selectionOf(
            ActivationId id, StoredActivation stored, PacksSession session, Optional<String> role, EffectiveTag tag) {
        ClosureSnapshot closure = session.snapshot();
        ProfileCandidates candidates = session.profileCandidates(role, EffectiveSet.queryOf(stored.document()));
        requireNoConflict(id, role.orElse(""), candidates);
        return new SelectionRead(
                stored, closure.fingerprint(), closure.closureFingerprint(), role, candidates, tag.token());
    }

    /**
     * {@code getModelRecommendation}, the activation's half (ADR-0080 decisions 7, 9 and 13; data-model "Refusal
     * order"): from one {@link PacksSession} under the stored document, the asked profile as the effective profile read
     * resolves it, whatever its state; with a step, the step judged as {@link #stepProfileSelection} judges it and its
     * model tier read at its place, a held step offered whether or not its mission type is in force; then the one
     * routing catalog in force, and the tag. A closure that cannot compose or merge answers its {@code 500} when the
     * session is first asked for what needs the merge. The routing slice then judges the task type, ranks and compares
     * {@code If-None-Match}, so every refusal comes before the {@code 304}.
     *
     * @param id the activation's id
     * @param query the task type, the profile, the step and the asked tier
     * @return the catalog in force, the profile's model and effort, the step's offer and the tag
     * @throws CharterRefusal with {@code activation_not_found}; {@code artifact_not_found} when no pack of the closure
     *     holds the profile; {@code mission_type_not_found} or {@code action_not_found} as {@link #stepProfileSelection}
     *     judges the step; the closure's own refusals; then {@code routing_catalog_not_in_force} when no catalog is in
     *     force, or {@code routing_catalog_conflict} when more than one is, naming the ids
     */
    public RecommendationRead modelRecommendation(ActivationId id, EffectiveTag.RecommendationQuery query) {
        Objects.requireNonNull(query, "query");
        StoredActivation stored = found(id);
        PacksSession session = open(stored);
        ClosureSnapshot closure = session.snapshot();
        InForceQuery inForce = EffectiveSet.queryOf(stored.document());
        CharterArtifact profile = session.profileResolution(query.profileId(), inForce)
                .map(ProfileResolution::profile)
                .orElseThrow(() -> notHeld(ArtifactKind.AGENT_PROFILE, query.profileId()));
        Optional<CostTier> offer = Optional.empty();
        if (query.step().isPresent()) {
            offer = stepOffer(session, query.step().get());
        }
        EffectiveHeldWinner winner = catalogInForce(id, session, inForce);
        CharterArtifact catalog = winnerOf(
                        session, winner.ref(), inForce, "The model recommendation under activation " + id.value())
                .artifact();
        ModelBearer bearer = profile instanceof ModelBearer each ? each : NO_MODEL;
        return new RecommendationRead(
                stored,
                closure.fingerprint(),
                closure.closureFingerprint(),
                winner,
                catalog,
                bearer.model(),
                bearer.effort(),
                offer,
                EffectiveTag.recommendation(stored.revision(), closure, query).token());
    }

    /** The asked step's model tier at its place, the step judged as {@link #stepProfileSelection} judges it. */
    private static Optional<CostTier> stepOffer(PacksSession session, EffectiveTag.StepRef step) {
        session.requireAction(step.missionType(), step.step());
        if (session.stepPlace(step.missionType(), step.step()).isEmpty()) {
            throw new CharterRefusal(
                    RefusalCode.ACTION_NOT_FOUND,
                    "No version of the mission type " + step.missionType()
                            + " in the activation's closure declares the step " + step.step() + ".");
        }
        return session.stepModelTier(step.missionType(), step.step());
    }

    /** The one routing catalog winner the document puts in force (ADR-0080 decision 9). */
    private static EffectiveHeldWinner catalogInForce(ActivationId id, PacksSession session, InForceQuery inForce) {
        List<EffectiveHeldWinner> inForceCatalogs = session.heldWinners(ArtifactKind.ROUTING_CATALOG).stream()
                .filter(each -> session.isInForce(each.ref(), inForce))
                .toList();
        if (inForceCatalogs.isEmpty()) {
            throw new CharterRefusal(
                    RefusalCode.ROUTING_CATALOG_NOT_IN_FORCE,
                    "The activation " + id.value() + " puts no routing catalog in force; select one.");
        }
        if (inForceCatalogs.size() > 1) {
            throw new CharterRefusal(
                    RefusalCode.ROUTING_CATALOG_CONFLICT,
                    "The activation " + id.value() + " puts the routing catalogs "
                            + inForceCatalogs.stream()
                                    .map(each -> each.ref().id())
                                    .collect(Collectors.joining(", "))
                            + " in force; deselect all but one.");
        }
        return inForceCatalogs.getFirst();
    }

    /** The conflicts selection cannot read past (ADR-0068 decision 7, ruling D3): the fallback's, then the pin's. */
    private static void requireNoConflict(ActivationId id, String role, ProfileCandidates candidates) {
        if (candidates.fallback() instanceof ProfileCandidates.Fallback.Conflict conflict) {
            throw new CharterRefusal(
                    RefusalCode.FALLBACK_PROFILE_CONFLICT,
                    "The packs " + String.join(", ", conflict.packs()) + " of the activation " + id.value()
                            + "'s closure, neither derived from the other, declare different fallback profiles.");
        }
        if (candidates.pin() instanceof ProfileCandidates.Pin.Conflict conflict) {
            throw new CharterRefusal(
                    RefusalCode.ROLE_PIN_CONFLICT,
                    "The packs " + String.join(", ", conflict.packs()) + " of the activation " + id.value()
                            + "'s closure pin the role " + role + " to different profiles.");
        }
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
     * tag of its own read ({@link EffectiveTag.Read#PROFILE}); a skill's, a glossary context's and a routing catalog's
     * answers are the reads of their literal routes, under the tags of their own reads too
     * ({@link EffectiveTag.Read#SKILL}, {@link EffectiveTag.Read#GLOSSARY}, {@link EffectiveTag.Read#ROUTING_CATALOG}),
     * so none shares a tag with the generic route.
     *
     * @param id the activation's id
     * @param kind the artifact's kind
     * @param artifactId the artifact's id, exactly as declared
     * @param depth the number of {@code suggests} hops of a profile's governance, at least 0
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the winner, or {@code NotModified} when the header matches the tag of this representation
     * @throws CharterRefusal with {@code activation_not_found}; {@code kind_not_found} when the packs slice does not list
     *     the kind, or lists one the activation does not resolve (an asset), naming its literal read;
     *     {@code pack_unreadable} when a pack the activation names is no longer configured, or the winner's
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
            case GLOSSARY -> EffectiveTag.Read.GLOSSARY;
            case ROUTING_CATALOG -> EffectiveTag.Read.ROUTING_CATALOG;
            default -> EffectiveTag.Read.ARTIFACT;
        };
        return artifactOf(stored, kind, artifactId, depth, ifNoneMatch, route);
    }

    /**
     * The winners of one kind under an activation, in id order (research R2, R3): {@code listEffectiveSkills},
     * {@code listEffectiveAssets}, {@code listEffectiveGlossaryContexts} and {@code listEffectiveRoutingCatalogs}. Each is the version the layer merge keeps
     * across the levels of the closure, with the pack and level that hold it and its policy, and no state; a skill's and
     * a glossary context's state is its own read's, and an asset has none. The whole set is answered, unpaged, from one
     * {@link PacksSession}, with no in-force resolution; what each winner's pack stores is the packs slice's to read,
     * against the source fingerprint of the answer.
     *
     * @param id the activation's id
     * @param kind {@code skill}, {@code asset}, {@code glossary} or {@code routing_catalog}
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
            case GLOSSARY -> EffectiveTag.Read.GLOSSARIES;
            case ROUTING_CATALOG -> EffectiveTag.Read.ROUTING_CATALOGS;
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
     * The glossary contexts in force under an activation (ADR-0076 decisions 1, 2 and 8): the vocabulary reads'
     * {@code listActivationVocabularyTerms}, {@code getActivationVocabularyContextive} and
     * {@code listActivationVocabularyTaboos}. Each is the winning copy of a {@code glossary} context whose state in the
     * effective set is {@link State#ACTIVE}, with the pack and level that hold it; a context the document deselects,
     * denies or does not select is not listed, and a context overrides across levels whole, so no copy the winner
     * replaced is named. The contexts are filtered from the one effective set the list read builds
     * ({@link #list}), in id order, from one {@link PacksSession}; what each winner's pack stores is the packs slice's
     * to read, against the source fingerprint of the answer.
     *
     * <p>The effective set is built before {@code If-None-Match} is compared, so the closure's refusals (such as
     * {@code pack_sibling_conflict}) are the answer whatever the header says (decision 8).
     *
     * @param id the activation's id
     * @param read {@link EffectiveTag.Read#VOCABULARY_TERMS}, {@link EffectiveTag.Read#VOCABULARY_CONTEXTIVE} or
     *     {@link EffectiveTag.Read#VOCABULARY_TABOOS}: the read whose tag the answer carries
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the contexts in force, or {@code NotModified} when the header matches the tag of the read
     * @throws IllegalArgumentException when {@code read} is not a vocabulary read
     * @throws CharterRefusal with {@code activation_not_found}, or as {@link #list} does
     */
    public Read<HeldWinners> contextsInForce(ActivationId id, EffectiveTag.Read read, @Nullable String ifNoneMatch) {
        Objects.requireNonNull(read, "read");
        StoredActivation stored = found(id);
        PacksSession session = open(stored);
        ClosureSnapshot closure = session.snapshot();
        EffectiveSet set = effectiveSet(stored, session);
        String etag = EffectiveTag.vocabulary(read, stored.revision(), closure).token();
        if (EntityTags.noneMatch(ifNoneMatch, etag)) {
            return new Read.NotModified<>(etag);
        }
        List<EffectiveHeldWinner> inForce = set.artifacts().stream()
                .filter(artifact -> artifact.kind() == ArtifactKind.GLOSSARY && artifact.state() == State.ACTIVE)
                .map(artifact ->
                        new EffectiveHeldWinner(artifact.ref(), artifact.packId(), artifact.level(), artifact.policy()))
                .toList();
        return new Read.Found<>(
                new HeldWinners(stored, closure.fingerprint(), closure.closureFingerprint(), inForce), etag);
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
        EffectiveHeldWinner winner = session.heldWinner(kind, artifactId).orElseThrow(() -> notHeld(kind, artifactId));
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
     * The winner of one artifact of any listed kind under an activation, whatever its state, with every artifact of a
     * listed kind the closure holds and the source fingerprint, all from one {@link PacksSession}: the referrer of
     * {@code listEffectiveArtifactReferences} (research R10). The winner is the layer merge's, the one rule
     * {@link #heldWinner} and {@link #artifact} answer: for a kind the activation resolves it is the winner the effective
     * set reads, and for an asset the winner {@link #heldWinner} reads. No state is resolved; the references are the
     * packs slice's to read from the winner's pack, against the source fingerprint of this answer. It has no tag: the
     * references read of a pack has none either.
     *
     * @param id the activation's id
     * @param kindToken the kind's token, as the request names it
     * @param artifactId the artifact's id, exactly as declared
     * @return the winner, the held artifacts and the fingerprints
     * @throws CharterRefusal with {@code activation_not_found}; then {@code kind_not_found} when the token names no kind
     *     the packs slice lists; then {@code artifact_not_found} when no pack of the closure holds the artifact; or as
     *     {@link #activation} does
     */
    public ListedWinner listedWinner(ActivationId id, String kindToken, String artifactId) {
        Objects.requireNonNull(kindToken, "kindToken");
        Objects.requireNonNull(artifactId, "artifactId");
        StoredActivation stored = found(id);
        List<ArtifactKind> listed = packs.listedKinds();
        ArtifactKind kind =
                ArtifactKind.fromToken(kindToken).filter(listed::contains).orElseThrow(() -> notServed(kindToken));
        PacksSession session = open(stored);
        ClosureSnapshot closure = session.snapshot();
        EffectiveHeldWinner winner = session.heldWinner(kind, artifactId).orElseThrow(() -> notHeld(kind, artifactId));
        Set<ArtifactRef> held = listed.stream()
                .flatMap(each -> session.heldWinners(each).stream())
                .map(EffectiveHeldWinner::ref)
                .collect(Collectors.toUnmodifiableSet());
        return new ListedWinner(stored, closure.fingerprint(), closure.closureFingerprint(), winner, held);
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
     * steps and its skeletons: the version the layer merge keeps and the pack that holds it, from one
     * {@link PacksSession}. The type is
     * answered whatever its state, as {@link #artifact} answers it; what the holding pack stores beside it (its skeletons
     * and step files) is the packs slice's to read, and the source fingerprint is what a caller checks that read
     * against. The activation is judged first, then the type.
     *
     * @param id the activation's id
     * @param missionTypeId the mission type's id, exactly as declared
     * @return the winning type, its pack and the source fingerprint of the read
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
        List<ArtifactKind> listed = packs.listedKinds();
        Set<ArtifactRef> held = merged.winners().keySet().stream()
                .filter(ref -> listed.contains(ref.kind()))
                .collect(Collectors.toUnmodifiableSet());
        return new EffectiveMissionTypeWinner(
                winner.packId(), winner.artifact(), session.snapshot().fingerprint(), held);
    }

    /**
     * The place of one step under an activation (research R8, A7, DIV-243), for the missions slice's read of a step that
     * the winning mission type does not declare: the pack whose version of the mission
     * type the step is read from ({@link PacksSession#stepPlace}), which keeps the step when only a version the winner
     * replaced declares it, and the source fingerprint, from one {@link PacksSession}. The names are judged first as the
     * action context judges them ({@link PacksSession#requireAction}), so the step, the context and the prompt reads
     * refuse one unknown step alike. What the place's pack stores of the mission type is the packs slice's to read,
     * against the fingerprint.
     *
     * @param id the activation's id
     * @param missionTypeId the mission type's id, exactly as declared
     * @param stepId the step's id, exactly as declared
     * @return the place's pack and the source fingerprint of the read
     * @throws CharterRefusal with {@code activation_not_found}; {@code pack_unreadable} when a pack the activation names
     *     is no longer configured, or as the merge refuses; {@code mission_type_not_found} or {@code action_not_found}
     *     when the merged closure holds no such mission type or step, and {@code action_not_found} when it holds the step
     *     and no version of the mission type at its place declares it; {@code stale_input} when the packs changed since
     *     the session was verified
     */
    public StepPlace stepPlace(ActivationId id, String missionTypeId, String stepId) {
        Objects.requireNonNull(missionTypeId, "missionTypeId");
        Objects.requireNonNull(stepId, "stepId");
        StoredActivation stored = found(id);
        PacksSession session = open(stored);
        session.requireAction(missionTypeId, stepId);
        String place = session.stepPlace(missionTypeId, stepId)
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.ACTION_NOT_FOUND,
                        "No version of the mission type " + missionTypeId
                                + " in the activation's closure declares the step " + stepId + "."));
        return new StepPlace(place, session.snapshot().fingerprint());
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
            throw notHeld(kind, artifactId);
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
        return new Read.Found<>(answerOf(stored, session, merged, ref, query, read, profileRoute), etag);
    }

    /**
     * {@code getEffectiveGlossaryTerm}: one term of a glossary context under an activation (#106, research R7). The
     * context is resolved as {@link #artifact} resolves the context's own read, its winner whatever its state; the term
     * is then found in that winner, so a term a copy the winner replaced holds is not answered, because a context
     * overrides whole (research R5). A term has no state of its own: the answer is the context's, with the context's
     * entity, from one {@link PacksSession}. The segments are judged in path order, each before any
     * {@code If-None-Match}, so the winner is read before the tag is compared; the tag names the context and the term
     * ({@link EffectiveTag.TermQuery}), so it is the tag of neither the context's read nor another term's.
     *
     * @param id the activation's id
     * @param contextId the context's id, as the request names it
     * @param termId the term's id, as the request names it
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the context's answer, or {@code NotModified} when the header matches the tag of this representation
     * @throws CharterRefusal with {@code activation_not_found}; {@code kind_not_found} when the packs slice does not
     *     list glossary contexts; {@code artifact_not_found} when no pack of the closure holds the context, then when the
     *     winning context does not hold the term, each naming what is missing; or as {@link #artifact} refuses
     * @throws IllegalStateException when the winner of a glossary context holds no terms: a broken invariant of the read
     */
    public Read<EffectiveArtifactAnswer> glossaryTerm(
            ActivationId id, String contextId, String termId, @Nullable String ifNoneMatch) {
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(termId, "termId");
        StoredActivation stored = found(id);
        if (!packs.listedKinds().contains(ArtifactKind.GLOSSARY)) {
            throw notServed(ArtifactKind.GLOSSARY.token());
        }
        PacksSession session = open(stored);
        ArtifactRef ref = new ArtifactRef(ArtifactKind.GLOSSARY, contextId);
        MergedSnapshot merged = session.merged();
        if (!merged.winners().containsKey(ref)) {
            throw notHeld(ArtifactKind.GLOSSARY, contextId);
        }
        InForceQuery query = EffectiveSet.queryOf(stored.document());
        String asked = "The read of the term " + termId + " of the glossary context " + contextId + " under activation "
                + id.value();
        EffectiveArtifactRead read = winnerOf(session, ref, query, asked);
        if (!(read.artifact() instanceof TermHolder context)) {
            throw new IllegalStateException(asked + ": the winner " + ref.urn() + " holds no terms.");
        }
        if (!context.holdsTerm(termId)) {
            throw new CharterRefusal(RefusalCode.ARTIFACT_NOT_FOUND, TermHolder.termNotHeld(contextId, termId));
        }
        String etag = EffectiveTag.term(
                        stored.revision(), session.snapshot(), new EffectiveTag.TermQuery(contextId, termId))
                .token();
        if (EntityTags.noneMatch(ifNoneMatch, etag)) {
            return new Read.NotModified<>(etag);
        }
        return new Read.Found<>(answerOf(stored, session, merged, ref, query, read, false), etag);
    }

    /**
     * The answer of one winner the session read: its facts from the one effective set of the request, over the merge the
     * request already asked for, and, on the profile route, its governance with the state of each entry.
     */
    private static EffectiveArtifactAnswer answerOf(
            StoredActivation stored,
            PacksSession session,
            MergedSnapshot merged,
            ArtifactRef ref,
            InForceQuery query,
            EffectiveArtifactRead read,
            boolean profileRoute) {
        ClosureSnapshot closure = session.snapshot();
        EffectiveSet set = EffectiveSet.of(stored.document(), closure, merged, session.inForce(query));
        Map<ArtifactRef, EffectiveArtifact> byRef = new HashMap<>();
        set.artifacts().forEach(artifact -> byRef.put(artifact.ref(), artifact));
        // The profiles slice's route answers the effective profile (R8): composed after the effective set, so a
        // closure that does not resolve is refused with its own code before any enhancement is judged.
        Optional<ProfileResolution> resolution = profileRoute
                ? Optional.of(session.profileResolution(ref.id(), query)
                        .orElseThrow(() -> new IllegalStateException("The merged closure holds " + ref.urn()
                                + ", and the session composed no effective profile of it.")))
                : Optional.empty();
        return new EffectiveArtifactAnswer(
                stored,
                closure.fingerprint(),
                closure.closureFingerprint(),
                inSet(byRef, ref),
                resolution.map(ProfileResolution::profile).orElse(read.artifact()),
                resolution.flatMap(
                        resolved -> read.governance().map(governance -> governed(governance, resolved, byRef, set))));
    }

    /**
     * A profile's governance with the state of each artifact it lists, from the one effective set of the request, and
     * its lineage and the profiles it enhances, as the session composed them.
     */
    private static EffectiveArtifactAnswer.Governance governed(
            GovernanceFacts governance,
            ProfileResolution resolution,
            Map<ArtifactRef, EffectiveArtifact> byRef,
            EffectiveSet set) {
        List<EffectiveArtifactAnswer.Entry> entries = governance.entries().stream()
                .map(entry -> new EffectiveArtifactAnswer.Entry(
                        inSet(byRef, entry.ref()),
                        entry.step(),
                        entry.relation(),
                        entry.via(),
                        entry.hops(),
                        new EffectiveArtifactAnswer.EdgeWords(entry.when(), entry.reason()),
                        entry.termId()))
                .toList();
        List<EffectiveArtifactAnswer.Suggested> suggested = governance.suggestionCandidates().stream()
                .flatMap(candidate -> set.suggested(candidate.ref(), candidate.when(), candidate.reason()).stream()
                        .map(artifact -> new EffectiveArtifactAnswer.Suggested(
                                artifact,
                                candidate.via(),
                                candidate.hops(),
                                new EffectiveArtifactAnswer.EdgeWords(candidate.when(), candidate.reason()))))
                .toList();
        return new EffectiveArtifactAnswer.Governance(entries, suggested, resolution.lineage(), resolution.enhances());
    }

    private static EffectiveArtifact inSet(Map<ArtifactRef, EffectiveArtifact> byRef, ArtifactRef ref) {
        EffectiveArtifact artifact = byRef.get(ref);
        if (artifact == null) {
            throw new IllegalStateException("The read lists " + ref.urn() + ", which the effective set does not hold.");
        }
        return artifact;
    }

    /**
     * The refusal of an artifact no pack of the closure holds. A glossary context is named as the pack read names it
     * (ruling {@code DM-01M7B1Q4BPQB95EVQBRWN4Q4F8}), so the twin answers the pack read's detail.
     */
    private static CharterRefusal notHeld(ArtifactKind kind, String artifactId) {
        if (kind == ArtifactKind.GLOSSARY) {
            return new CharterRefusal(RefusalCode.ARTIFACT_NOT_FOUND, TermHolder.contextNotHeld(artifactId));
        }
        return new CharterRefusal(
                RefusalCode.ARTIFACT_NOT_FOUND,
                "No pack of the activation's closure holds the " + kind.token() + " " + artifactId + ".");
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

    /**
     * One verified read of the closure the document names, refused when a pack it names is no longer configured; the
     * tensions read opens its session through it too ({@link TensionReadService}).
     */
    PacksSession open(StoredActivation stored) {
        PacksSession session = packs.session(stored.document().packIds());
        if (!session.snapshot().resolved()) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    "The activation builds on "
                            + String.join(", ", session.snapshot().unknownPacks())
                            + ", which no pack is configured as any more.");
        }
        return session;
    }

    /** The stored activation, or {@code activation_not_found}; the tensions read finds it through it too. */
    StoredActivation found(ActivationId id) {
        return repository
                .find(id)
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.ACTIVATION_NOT_FOUND, "No activation is stored as '" + id + "'."));
    }
}
