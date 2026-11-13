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
     *     the packs slice does not list, or as {@link #artifact(ActivationId, ArtifactKind, String, int, String)} does
     */
    public Read<EffectiveArtifactAnswer> artifact(
            ActivationId id, String kindToken, String artifactId, int depth, @Nullable String ifNoneMatch) {
        StoredActivation stored = found(id);
        ArtifactKind kind = ArtifactKind.fromToken(kindToken)
                .filter(packs.listedKinds()::contains)
                .orElseThrow(() -> notServed(kindToken));
        return artifactOf(stored, kind, artifactId, depth, ifNoneMatch, false);
    }

    /**
     * {@code getEffectiveArtifact}, for a caller that holds the kind already (the profiles slice's route, the mission
     * type route): the same read as the token form, judged in the same order.
     *
     * <p>One {@link PacksSession} answers it: the merge decides whether a pack of the closure holds the artifact, before
     * any {@code If-None-Match} is compared, so a name no pack holds is a {@code 404} whatever the header says; the tag
     * is the {@link EffectiveTag} of the read, which names the kind, the id and the depth and, for an agent profile, its
     * own read; and only a request whose header does not match asks the session for the entity and the in-force
     * resolution, once each. An agent profile's answer carries its governance with the state of each entry.
     *
     * @param id the activation's id
     * @param kind the artifact's kind
     * @param artifactId the artifact's id, exactly as declared
     * @param depth the number of {@code suggests} hops of a profile's governance, at least 0
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the winner, or {@code NotModified} when the header matches the tag of this representation
     * @throws CharterRefusal with {@code activation_not_found}; {@code kind_not_found} when the packs slice does not list
     *     the kind; {@code pack_unreadable} when a pack the activation names is no longer configured, or the winner's
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
        return artifactOf(stored, kind, artifactId, depth, ifNoneMatch, kind == ArtifactKind.AGENT_PROFILE);
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
     * The place of one step under an activation (research R8, A7, DIV-243), for the missions slice's read of a step that
     * the winning mission type does not declare, and of a step contract's step: the pack whose version of the mission
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
     * The one read behind both routes. {@code profileRoute} is true for the profiles slice's route, whose body carries
     * the governance and so names {@link EffectiveTag.Read#PROFILE}; the generic route names
     * {@link EffectiveTag.Read#ARTIFACT} for every kind, agent profile included, because its body never renders the
     * governance, so the two URLs never share a tag.
     */
    private Read<EffectiveArtifactAnswer> artifactOf(
            StoredActivation stored,
            ArtifactKind kind,
            String artifactId,
            int depth,
            @Nullable String ifNoneMatch,
            boolean profileRoute) {
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
        String etag = (profileRoute
                        ? EffectiveTag.profile(stored.revision(), closure, asked)
                        : EffectiveTag.artifact(stored.revision(), closure, asked))
                .token();
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
