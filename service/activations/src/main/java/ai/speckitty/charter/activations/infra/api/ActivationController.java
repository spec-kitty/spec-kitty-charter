package ai.speckitty.charter.activations.infra.api;

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
import ai.speckitty.charter.kernel.transport.ReservedOperation;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The activation set (ADR-0020), declared in full and reserved (ADR-0020 D8, #125): every operation answers
 * {@code 501 Not Implemented}. Each operation carries its own {@link ReservedOperation} mark, so an operation is built
 * by removing its mark alone, while the others stay reserved. The composition root's
 * {@code ReservedOperationInterceptor} answers before the request is bound, so a marked method is never reached; each
 * throws the same answer in case one is. A marked method reads and stores nothing ({@code HexagonalArchitectureTest}).
 * It lives in {@code activations.infra.api}: the activation set is a slice of its own, over the packs slice.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class ActivationController implements ActivationApi {

    /** For Spring, which finds the controller by component scan. */
    public ActivationController() {}

    @Override
    @ReservedOperation
    public ResponseEntity<ActivationChange> applyActivationPreset(
            String activationId, String presetId, PresetApplication presetApplication, @Nullable String ifMatch) {
        throw notBuilt();
    }

    @Override
    @ReservedOperation
    public ResponseEntity<Activation> createActivation(ActivationConfig activationConfig) {
        throw notBuilt();
    }

    @Override
    @ReservedOperation
    public ResponseEntity<ActivationChange> deactivateActivationArtifact(
            String activationId,
            String kind,
            String artifactId,
            ExclusionRequest exclusionRequest,
            @Nullable String ifMatch) {
        throw notBuilt();
    }

    @Override
    @ReservedOperation
    public ResponseEntity<Void> deleteActivation(String activationId, @Nullable String ifMatch) {
        throw notBuilt();
    }

    @Override
    @ReservedOperation
    public ResponseEntity<Activation> getActivation(String activationId, @Nullable String ifNoneMatch) {
        throw notBuilt();
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

    /**
     * The closure read (#360). Declared without {@code x-reserved} in the contract, it is marked here until it is
     * built, so it answers {@code 501} like a reserved operation in the meantime.
     */
    @Override
    @ReservedOperation
    public ResponseEntity<ActivationPackList> listActivationPacks(String activationId, @Nullable String ifNoneMatch) {
        throw notBuilt();
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
    @ReservedOperation
    public ResponseEntity<ActivationChange> reactivateActivationArtifact(
            String activationId, String kind, String artifactId, @Nullable String ifMatch) {
        throw notBuilt();
    }

    @Override
    @ReservedOperation
    public ResponseEntity<Activation> replaceActivation(
            String activationId,
            ActivationConfig activationConfig,
            @Nullable String ifMatch,
            @Nullable String ifNoneMatch) {
        throw notBuilt();
    }

    @Override
    @ReservedOperation
    public ResponseEntity<ActivationChange> selectActivationArtifact(
            String activationId,
            String kind,
            String artifactId,
            SelectionRequest selectionRequest,
            @Nullable String ifMatch) {
        throw notBuilt();
    }

    @Override
    @ReservedOperation
    public ResponseEntity<ActivationChange> unselectActivationArtifact(
            String activationId, String kind, String artifactId, @Nullable String ifMatch) {
        throw notBuilt();
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
