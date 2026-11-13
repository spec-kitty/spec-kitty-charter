package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.activations.application.ActivationExclusionService;
import ai.speckitty.charter.activations.application.ActivationPresetService;
import ai.speckitty.charter.activations.application.ActivationSelectionService;
import ai.speckitty.charter.activations.application.ActivationService;
import ai.speckitty.charter.activations.application.ActivationWrites;
import ai.speckitty.charter.activations.application.EffectiveArtifactAnswer;
import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.EffectiveSet;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.api.ActivationApi;
import ai.speckitty.charter.api.model.Activation;
import ai.speckitty.charter.api.model.ActivationChange;
import ai.speckitty.charter.api.model.ActivationConfig;
import ai.speckitty.charter.api.model.ActivationPackList;
import ai.speckitty.charter.api.model.ActivationSkippedReport;
import ai.speckitty.charter.api.model.EffectiveActionContext;
import ai.speckitty.charter.api.model.EffectiveArtifactList;
import ai.speckitty.charter.api.model.EffectiveArtifactRead;
import ai.speckitty.charter.api.model.EffectiveGraph;
import ai.speckitty.charter.api.model.EffectiveStepPrompt;
import ai.speckitty.charter.api.model.ExclusionRequest;
import ai.speckitty.charter.api.model.PresetApplication;
import ai.speckitty.charter.api.model.SelectionRequest;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.web.InvalidParameter;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The activation set (ADR-0020). The register is built: {@code createActivation}, {@code getActivation},
 * {@code replaceActivation}, {@code deleteActivation} and the closure read {@code listActivationPacks} answer through
 * {@link ActivationService}; the per-artifact writes ({@code selectActivationArtifact},
 * {@code unselectActivationArtifact}, {@code deactivateActivationArtifact} and {@code reactivateActivationArtifact}) and
 * the preset write ({@code applyActivationPreset}) through {@link ActivationSelectionService},
 * {@link ActivationExclusionService} and {@link ActivationPresetService}, each answering the {@code ActivationChange}
 * with the stored revision as its {@code ETag}. A refusal they throw is answered by the problem handlers. The faults
 * {@link ActivationBodyAdvice} found in a write's body ({@link DeferredBody}) go to the use case, which refuses them
 * once the preconditions hold. {@code getActivation} and the effective reads ({@code listEffectiveArtifacts},
 * {@code getActivationSkippedReport}, {@code getEffectiveGraph}, {@code getEffectiveActionContext} and
 * {@code getEffectiveStepPrompt}) answer through {@link EffectiveReadService}. Every operation is built, so none carries a {@code ReservedOperation} mark
 * (ADR-0020 D8); an operation reserved later is marked on its method alone, and the composition root's
 * {@code ReservedOperationInterceptor} answers a marked one {@code 501} before the request is bound. A marked method reads
 * and stores nothing ({@code HexagonalArchitectureTest}).
 *
 * <p>The reads answer {@code 304} themselves when the {@code If-None-Match} matches ({@link EntityTags#noneMatch}, a
 * weak comparison), with the {@code ETag} and no body. Spring's {@code HttpEntityMethodProcessor} re-checks only a
 * {@code 200} carrying an {@code ETag}, by the same weak comparison, so the two agree: a tag the service found not
 * matching is one Spring finds not matching. {@code getActivation}'s {@code ETag} is the strong closure tag, over the
 * revision and the closure's own digest, because its {@code denyNotApplied} is judged against the packs of the closure
 * (decision {@code 01M6Y11E55YP5TG1EJ1MDKCFRP}); the four effective reads' tags also name their representation. The four
 * effective reads are verified reads and send the {@code Charter-Source-Fingerprint} they verified on a {@code 200}; the
 * document read and the closure read send none (the closure read reports a change through its {@code ETag}). The
 * writes answer the revision as their {@code ETag} and take it, or the {@code getActivation} tag, as {@code If-Match}.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class ActivationController implements ActivationApi {

    private final ActivationService activations;
    private final ActivationSelectionService selections;
    private final ActivationExclusionService exclusions;
    private final ActivationPresetService presets;
    private final ActivationMapper mapper;
    private final ActivationChangeMapper changes;
    private final EffectiveReadService effective;
    private final EffectiveReadMapper reads;
    private final EffectiveArtifactMapper artifacts;

    /**
     * A controller over the activation register and its per-artifact writes.
     *
     * @param activations the register's use cases
     * @param selections the select and the unselect
     * @param exclusions the deactivate and the re-activate
     * @param presets the preset write
     * @param mapper the document's wire mapper
     * @param changes the per-artifact writes' wire mapper
     * @param effective the effective reads and the document read
     * @param reads the effective reads' wire mapper
     * @param artifacts the wire mapper of the read of one artifact
     */
    @Autowired
    public ActivationController(
            ActivationService activations,
            ActivationSelectionService selections,
            ActivationExclusionService exclusions,
            ActivationPresetService presets,
            ActivationMapper mapper,
            ActivationChangeMapper changes,
            EffectiveReadService effective,
            EffectiveReadMapper reads,
            EffectiveArtifactMapper artifacts) {
        this.activations = Objects.requireNonNull(activations, "activations");
        this.selections = Objects.requireNonNull(selections, "selections");
        this.exclusions = Objects.requireNonNull(exclusions, "exclusions");
        this.presets = Objects.requireNonNull(presets, "presets");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.changes = Objects.requireNonNull(changes, "changes");
        this.effective = Objects.requireNonNull(effective, "effective");
        this.reads = Objects.requireNonNull(reads, "reads");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
    }

    @Override
    public ResponseEntity<ActivationChange> applyActivationPreset(
            String activationId, String presetId, PresetApplication presetApplication, @Nullable String ifMatch) {
        boolean force = Boolean.TRUE.equals(presetApplication.getForce());
        return changed(
                presets.apply(new ActivationId(activationId), presetId, force, DeferredBody.recorded(), ifMatch));
    }

    @Override
    public ResponseEntity<Activation> createActivation(ActivationConfig activationConfig) {
        StoredActivation stored = activations.create(mapper.toDocument(activationConfig));
        return created(stored, mapper.toActivation(stored));
    }

    @Override
    public ResponseEntity<ActivationChange> deactivateActivationArtifact(
            String activationId,
            String kind,
            String artifactId,
            ExclusionRequest exclusionRequest,
            @Nullable String ifMatch) {
        Optional<List<String>> cascade =
                Optional.ofNullable(exclusionRequest.getCascade()).map(List::copyOf);
        return changed(exclusions.deactivate(
                new ActivationId(activationId),
                kind,
                artifactId,
                cascade,
                depthOf(exclusionRequest.getDepth()),
                DeferredBody.recorded(),
                ifMatch));
    }

    @Override
    public ResponseEntity<Void> deleteActivation(String activationId, @Nullable String ifMatch) {
        activations.delete(new ActivationId(activationId), ifMatch);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Activation> getActivation(String activationId, @Nullable String ifNoneMatch) {
        return answer(
                effective.activation(new ActivationId(activationId), ifNoneMatch),
                viewed -> mapper.toActivation(viewed.stored(), viewed.denyNotApplied()));
    }

    @Override
    public ResponseEntity<ActivationSkippedReport> getActivationSkippedReport(
            String activationId, @Nullable String ifNoneMatch) {
        return verified(
                effective.skipped(new ActivationId(activationId), ifNoneMatch),
                EffectiveReadService.Report::sourceFingerprint,
                reads::toSkipped);
    }

    @Override
    public ResponseEntity<EffectiveActionContext> getEffectiveActionContext(
            String activationId, String missionType, String action, @Nullable String ifNoneMatch, Integer depth) {
        boolean depthGiven = queryParamGiven("depth");
        return verified(
                effective.actionContext(
                        new ActivationId(activationId),
                        new EffectiveTag.ContextQuery(missionType, action, depth),
                        ifNoneMatch),
                EffectiveReadService.ContextRead::sourceFingerprint,
                read -> reads.toContext(read, depthGiven));
    }

    @Override
    public ResponseEntity<EffectiveArtifactRead> getEffectiveArtifact(
            String activationId, String kind, String artifactId, @Nullable String ifNoneMatch, Integer depth) {
        OptionalInt given = queryParamGiven("depth") ? OptionalInt.of(depth) : OptionalInt.empty();
        return verified(
                effective.artifact(new ActivationId(activationId), kind, artifactId, depth, ifNoneMatch),
                EffectiveArtifactAnswer::sourceFingerprint,
                answer -> artifacts.toRead(answer, given));
    }

    @Override
    public ResponseEntity<EffectiveGraph> getEffectiveGraph(String activationId, @Nullable String ifNoneMatch) {
        return verified(
                effective.graph(new ActivationId(activationId), ifNoneMatch),
                EffectiveReadService.GraphRead::sourceFingerprint,
                reads::toGraph);
    }

    @Override
    public ResponseEntity<EffectiveStepPrompt> getEffectiveStepPrompt(
            String activationId, String missionType, String action, @Nullable String ifNoneMatch) {
        return verified(
                effective.prompt(
                        new ActivationId(activationId), new EffectiveTag.PromptQuery(missionType, action), ifNoneMatch),
                EffectiveReadService.PromptRead::sourceFingerprint,
                reads::toPrompt);
    }

    @Override
    public ResponseEntity<ActivationPackList> listActivationPacks(String activationId, @Nullable String ifNoneMatch) {
        Function<ClosureSnapshot, ActivationPackList> body = snapshot -> mapper.toPackList(activationId, snapshot);
        return answer(activations.packs(new ActivationId(activationId), ifNoneMatch), body);
    }

    @Override
    public ResponseEntity<EffectiveArtifactList> listEffectiveArtifacts(
            String activationId,
            @Nullable String ifNoneMatch,
            @Nullable String kind,
            @Nullable String state,
            Integer pageSize,
            @Nullable String pageCursor) {
        EffectiveReadService.PageQuery query = new EffectiveReadService.PageQuery(
                Optional.ofNullable(kind).map(ActivationController::kindFilter),
                Optional.ofNullable(state).map(ActivationController::stateFilter),
                pageSize,
                Optional.ofNullable(pageCursor).map(EffectiveCursor::decode));
        try {
            return verified(
                    effective.list(new ActivationId(activationId), query, ifNoneMatch),
                    EffectiveReadService.Page::sourceFingerprint,
                    page -> reads.toList(page, queryParamGiven("pageSize")));
        } catch (EffectiveReadService.StaleCursorException anotherSet) {
            throw new InvalidParameter(
                    InvalidParameter.In.QUERY,
                    "pageCursor",
                    Objects.requireNonNull(pageCursor, "pageCursor"),
                    "which belongs to another set of effective artifacts; read the first page again");
        }
    }

    @Override
    public ResponseEntity<ActivationChange> reactivateActivationArtifact(
            String activationId, String kind, String artifactId, @Nullable String ifMatch) {
        return changed(exclusions.reactivate(new ActivationId(activationId), kind, artifactId, ifMatch));
    }

    @Override
    public ResponseEntity<Activation> replaceActivation(
            String activationId,
            ActivationConfig activationConfig,
            @Nullable String ifMatch,
            @Nullable String ifNoneMatch) {
        ActivationService.Written written = activations.put(
                new ActivationId(activationId), mapper.toDocument(activationConfig), ifMatch, ifNoneMatch);
        if (written.created()) {
            return created(written.stored(), mapper.toReplaced(written));
        }
        return ResponseEntity.ok()
                .eTag(EntityTags.quote(written.stored().revision().token()))
                .body(mapper.toReplaced(written));
    }

    @Override
    public ResponseEntity<ActivationChange> selectActivationArtifact(
            String activationId,
            String kind,
            String artifactId,
            SelectionRequest selectionRequest,
            @Nullable String ifMatch) {
        Optional<List<String>> cascade =
                Optional.ofNullable(selectionRequest.getCascade()).map(List::copyOf);
        boolean includeDeactivated = Boolean.TRUE.equals(selectionRequest.getIncludeDeactivated());
        return changed(selections.select(
                new ActivationId(activationId),
                kind,
                artifactId,
                cascade,
                includeDeactivated,
                depthOf(selectionRequest.getDepth()),
                DeferredBody.recorded(),
                ifMatch));
    }

    @Override
    public ResponseEntity<ActivationChange> unselectActivationArtifact(
            String activationId, String kind, String artifactId, @Nullable String ifMatch) {
        return changed(selections.unselect(new ActivationId(activationId), kind, artifactId, ifMatch));
    }

    /** The body's {@code depth}, which its guard has judged: empty when the body did not send one. */
    private static OptionalInt depthOf(@Nullable Integer depth) {
        return depth == null ? OptionalInt.empty() : OptionalInt.of(depth);
    }

    /** A per-artifact write's answer: {@code 200} with the stored revision as the {@code ETag}, a no-op's unchanged. */
    private ResponseEntity<ActivationChange> changed(ActivationWrites.Outcome outcome) {
        return ResponseEntity.ok()
                .eTag(EntityTags.quote(outcome.stored().revision().token()))
                .body(changes.toChange(outcome));
    }

    private ResponseEntity<Activation> created(StoredActivation stored, Activation body) {
        return ResponseEntity.created(URI.create(mapper.self(stored.id().value())))
                .eTag(EntityTags.quote(stored.revision().token()))
                .body(body);
    }

    /** A read's answer: {@code 304} with the {@code ETag} and no body, or {@code 200} with both. */
    private static <T, B> ResponseEntity<B> answer(ActivationService.Read<T> read, Function<T, B> body) {
        return switch (read) {
            case ActivationService.Read.NotModified<T>(String etag) ->
                ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(EntityTags.quote(etag))
                        .build();
            case ActivationService.Read.Found<T>(T value, String etag) ->
                ResponseEntity.ok().eTag(EntityTags.quote(etag)).body(body.apply(value));
        };
    }

    /**
     * A verified read's answer: as {@link #answer}, and on a {@code 200} the {@code Charter-Source-Fingerprint} the read
     * verified; a {@code 304} sends the tag alone.
     */
    private static <T, B> ResponseEntity<B> verified(
            ActivationService.Read<T> read, Function<T, String> fingerprint, Function<T, B> body) {
        return switch (read) {
            case ActivationService.Read.NotModified<T>(String etag) ->
                ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(EntityTags.quote(etag))
                        .build();
            case ActivationService.Read.Found<T>(T value, String etag) ->
                ResponseEntity.ok()
                        .eTag(EntityTags.quote(etag))
                        .header(HeaderNames.SOURCE_FINGERPRINT, fingerprint.apply(value))
                        .body(body.apply(value));
        };
    }

    /** Whether the request named a query parameter that has a default, so that this is read from the request itself. */
    private static boolean queryParamGiven(String name) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .build()
                .getQueryParams()
                .containsKey(name);
    }

    /** The kind filter: an activatable kind, or a {@code 400} problem without a code that lists them (#411). */
    private static ArtifactKind kindFilter(String token) {
        return ArtifactKind.fromToken(token)
                .filter(ArtifactKind::activatable)
                .orElseThrow(() -> new InvalidParameter(
                        InvalidParameter.In.QUERY,
                        "kind",
                        token,
                        InvalidParameter.oneOf(Arrays.stream(ArtifactKind.values())
                                .filter(ArtifactKind::activatable)
                                .map(ArtifactKind::token)
                                .toList())));
    }

    /** The state filter: one of the three states, or a {@code 400} problem without a code that lists them (#411). */
    private static EffectiveSet.State stateFilter(String token) {
        return Arrays.stream(EffectiveSet.State.values())
                .filter(state -> state.token().equals(token))
                .findFirst()
                .orElseThrow(() -> new InvalidParameter(
                        InvalidParameter.In.QUERY,
                        "state",
                        token,
                        InvalidParameter.oneOf(Arrays.stream(EffectiveSet.State.values())
                                .map(EffectiveSet.State::token)
                                .toList())));
    }
}
