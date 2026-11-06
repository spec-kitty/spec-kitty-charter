package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.activations.application.ActivationExclusionService;
import ai.speckitty.charter.activations.application.ActivationSelectionService;
import ai.speckitty.charter.activations.application.ActivationService;
import ai.speckitty.charter.activations.application.ActivationWrites;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.api.ActivationApi;
import ai.speckitty.charter.api.model.Activation;
import ai.speckitty.charter.api.model.ActivationChange;
import ai.speckitty.charter.api.model.ActivationConfig;
import ai.speckitty.charter.api.model.ActivationPackList;
import ai.speckitty.charter.api.model.ActivationSkippedReport;
import ai.speckitty.charter.api.model.EffectiveActionContext;
import ai.speckitty.charter.api.model.EffectiveArtifactList;
import ai.speckitty.charter.api.model.EffectiveGraph;
import ai.speckitty.charter.api.model.ExclusionRequest;
import ai.speckitty.charter.api.model.PresetApplication;
import ai.speckitty.charter.api.model.SelectionRequest;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.kernel.transport.ReservedOperation;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The activation set (ADR-0020). The register is built: {@code createActivation}, {@code getActivation},
 * {@code replaceActivation}, {@code deleteActivation} and the closure read {@code listActivationPacks} answer through
 * {@link ActivationService}; the per-artifact writes ({@code selectActivationArtifact},
 * {@code unselectActivationArtifact}, {@code deactivateActivationArtifact} and {@code reactivateActivationArtifact})
 * through {@link ActivationSelectionService} and {@link ActivationExclusionService}, each answering the
 * {@code ActivationChange} with the stored revision as its {@code ETag}. A refusal they throw is answered by the problem
 * handlers. The other five operations stay reserved (ADR-0020 D8): each carries its own {@link ReservedOperation} mark,
 * so an operation is built by removing its mark alone. The composition root's {@code ReservedOperationInterceptor} answers a marked one {@code 501} before
 * the request is bound; each throws the same answer in case one is reached. A marked method reads and stores nothing
 * ({@code HexagonalArchitectureTest}).
 *
 * <p>The reads answer {@code 304} themselves when the {@code If-None-Match} matches ({@link EntityTags#noneMatch}, a
 * weak comparison), with the {@code ETag} and no body. Spring's {@code HttpEntityMethodProcessor} re-checks only a
 * {@code 200} carrying an {@code ETag}, by the same weak comparison, so the two agree: a tag the service found not
 * matching is one Spring finds not matching. Neither read sends {@code Charter-Source-Fingerprint}: the document read
 * depends only on the stored document (ADR-0059 D5), and the closure read reports a change through its {@code ETag},
 * which covers the revision and the closure's fingerprint.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class ActivationController implements ActivationApi {

    private final ActivationService activations;
    private final ActivationSelectionService selections;
    private final ActivationExclusionService exclusions;
    private final ActivationMapper mapper;
    private final ActivationChangeMapper changes;

    /**
     * A controller over the activation register and its per-artifact writes.
     *
     * @param activations the register's use cases
     * @param selections the select and the unselect
     * @param exclusions the deactivate and the re-activate
     * @param mapper the document's wire mapper
     * @param changes the per-artifact writes' wire mapper
     */
    @Autowired
    public ActivationController(
            ActivationService activations,
            ActivationSelectionService selections,
            ActivationExclusionService exclusions,
            ActivationMapper mapper,
            ActivationChangeMapper changes) {
        this.activations = Objects.requireNonNull(activations, "activations");
        this.selections = Objects.requireNonNull(selections, "selections");
        this.exclusions = Objects.requireNonNull(exclusions, "exclusions");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.changes = Objects.requireNonNull(changes, "changes");
    }

    @Override
    @ReservedOperation
    public ResponseEntity<ActivationChange> applyActivationPreset(
            String activationId, String presetId, PresetApplication presetApplication, @Nullable String ifMatch) {
        throw notBuilt();
    }

    @Override
    public ResponseEntity<Activation> createActivation(ActivationConfig activationConfig) {
        StoredActivation stored = activations.create(mapper.toDocument(activationConfig));
        return created(stored);
    }

    @Override
    public ResponseEntity<ActivationChange> deactivateActivationArtifact(
            String activationId,
            String kind,
            String artifactId,
            ExclusionRequest exclusionRequest,
            @Nullable String ifMatch) {
        Optional<List<String>> cascade = Optional.ofNullable(exclusionRequest.getCascade())
                .map(tokens -> tokens.stream()
                        .map(ExclusionRequest.CascadeEnum::getValue)
                        .toList());
        return changed(exclusions.deactivate(new ActivationId(activationId), kind, artifactId, cascade, ifMatch));
    }

    @Override
    public ResponseEntity<Void> deleteActivation(String activationId, @Nullable String ifMatch) {
        activations.delete(new ActivationId(activationId), ifMatch);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Activation> getActivation(String activationId, @Nullable String ifNoneMatch) {
        return answer(activations.get(new ActivationId(activationId), ifNoneMatch), mapper::toActivation);
    }

    @Override
    @ReservedOperation
    public ResponseEntity<ActivationSkippedReport> getActivationSkippedReport(
            String activationId, @Nullable String ifNoneMatch) {
        throw notBuilt();
    }

    @Override
    @ReservedOperation
    public ResponseEntity<EffectiveActionContext> getEffectiveActionContext(
            String activationId, String missionType, String action, @Nullable String ifNoneMatch, Integer depth) {
        throw notBuilt();
    }

    @Override
    @ReservedOperation
    public ResponseEntity<EffectiveGraph> getEffectiveGraph(String activationId, @Nullable String ifNoneMatch) {
        throw notBuilt();
    }

    @Override
    public ResponseEntity<ActivationPackList> listActivationPacks(String activationId, @Nullable String ifNoneMatch) {
        Function<ClosureSnapshot, ActivationPackList> body = snapshot -> mapper.toPackList(activationId, snapshot);
        return answer(activations.packs(new ActivationId(activationId), ifNoneMatch), body);
    }

    @Override
    @ReservedOperation
    public ResponseEntity<EffectiveArtifactList> listEffectiveArtifacts(
            String activationId,
            @Nullable String ifNoneMatch,
            @Nullable String kind,
            @Nullable String state,
            Integer pageSize,
            @Nullable String pageCursor) {
        throw notBuilt();
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
            return created(written.stored());
        }
        return ResponseEntity.ok()
                .eTag(EntityTags.quote(written.stored().revision().token()))
                .body(mapper.toActivation(written.stored()));
    }

    @Override
    public ResponseEntity<ActivationChange> selectActivationArtifact(
            String activationId,
            String kind,
            String artifactId,
            SelectionRequest selectionRequest,
            @Nullable String ifMatch) {
        Optional<List<String>> cascade = Optional.ofNullable(selectionRequest.getCascade())
                .map(tokens -> tokens.stream()
                        .map(SelectionRequest.CascadeEnum::getValue)
                        .toList());
        boolean includeDeactivated = Boolean.TRUE.equals(selectionRequest.getIncludeDeactivated());
        return changed(selections.select(
                new ActivationId(activationId), kind, artifactId, cascade, includeDeactivated, ifMatch));
    }

    @Override
    public ResponseEntity<ActivationChange> unselectActivationArtifact(
            String activationId, String kind, String artifactId, @Nullable String ifMatch) {
        return changed(selections.unselect(new ActivationId(activationId), kind, artifactId, ifMatch));
    }

    /** A per-artifact write's answer: {@code 200} with the stored revision as the {@code ETag}, a no-op's unchanged. */
    private ResponseEntity<ActivationChange> changed(ActivationWrites.Outcome outcome) {
        return ResponseEntity.ok()
                .eTag(EntityTags.quote(outcome.stored().revision().token()))
                .body(changes.toChange(outcome));
    }

    private ResponseEntity<Activation> created(StoredActivation stored) {
        return ResponseEntity.created(URI.create(mapper.self(stored.id().value())))
                .eTag(EntityTags.quote(stored.revision().token()))
                .body(mapper.toActivation(stored));
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
     * The answer of a reserved operation, should one ever be reached past the interceptor.
     *
     * @return a {@code 501} that {@code ProblemHandler} renders as a {@code Problem} without a code
     */
    private static ResponseStatusException notBuilt() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
