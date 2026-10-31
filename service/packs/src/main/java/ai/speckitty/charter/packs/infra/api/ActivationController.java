package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.ActivationApi;
import ai.speckitty.charter.api.model.Activation;
import ai.speckitty.charter.api.model.ActivationChange;
import ai.speckitty.charter.api.model.ActivationConfig;
import ai.speckitty.charter.api.model.ActivationSkippedReport;
import ai.speckitty.charter.api.model.EffectiveActionContext;
import ai.speckitty.charter.api.model.EffectiveArtifactList;
import ai.speckitty.charter.api.model.EffectiveGraph;
import ai.speckitty.charter.api.model.ExclusionRequest;
import ai.speckitty.charter.api.model.PresetApplication;
import ai.speckitty.charter.api.model.SelectionRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The activation set (ADR-0020), declared in full and reserved (ADR-0020 D8, #125): every operation answers
 * {@code 501 Not Implemented}. {@link ReservedOperationInterceptor} answers before the request is bound, so these
 * methods are never reached; each throws the same answer in case one is. The controller depends on nothing: no
 * activation is stored or read until the operations are built. It lives in {@code packs.infra.api}, the one adapter
 * allowed to name the generated transport types; the built activation set becomes a slice of its own.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
@ReservedOperation
public class ActivationController implements ActivationApi {

    /** For Spring, which finds the controller by component scan. */
    public ActivationController() {}

    @Override
    public ResponseEntity<ActivationChange> applyActivationPreset(
            String activationId, String presetId, PresetApplication presetApplication, @Nullable String ifMatch) {
        throw ReservedOperationInterceptor.notBuilt();
    }

    @Override
    public ResponseEntity<Activation> createActivation(ActivationConfig activationConfig) {
        throw ReservedOperationInterceptor.notBuilt();
    }

    @Override
    public ResponseEntity<ActivationChange> deactivateActivationArtifact(
            String activationId,
            String kind,
            String artifactId,
            ExclusionRequest exclusionRequest,
            @Nullable String ifMatch) {
        throw ReservedOperationInterceptor.notBuilt();
    }

    @Override
    public ResponseEntity<Void> deleteActivation(String activationId, @Nullable String ifMatch) {
        throw ReservedOperationInterceptor.notBuilt();
    }

    @Override
    public ResponseEntity<Activation> getActivation(String activationId) {
        throw ReservedOperationInterceptor.notBuilt();
    }

    @Override
    public ResponseEntity<ActivationSkippedReport> getActivationSkippedReport(String activationId) {
        throw ReservedOperationInterceptor.notBuilt();
    }

    @Override
    public ResponseEntity<EffectiveActionContext> getEffectiveActionContext(
            String activationId, String missionType, String action, Integer depth) {
        throw ReservedOperationInterceptor.notBuilt();
    }

    @Override
    public ResponseEntity<EffectiveGraph> getEffectiveGraph(String activationId) {
        throw ReservedOperationInterceptor.notBuilt();
    }

    @Override
    public ResponseEntity<EffectiveArtifactList> listEffectiveArtifacts(
            String activationId,
            @Nullable String kind,
            @Nullable String state,
            Integer pageSize,
            @Nullable String pageCursor) {
        throw ReservedOperationInterceptor.notBuilt();
    }

    @Override
    public ResponseEntity<ActivationChange> reactivateActivationArtifact(
            String activationId, String kind, String artifactId, @Nullable String ifMatch) {
        throw ReservedOperationInterceptor.notBuilt();
    }

    @Override
    public ResponseEntity<Activation> replaceActivation(
            String activationId,
            ActivationConfig activationConfig,
            @Nullable String ifMatch,
            @Nullable String ifNoneMatch) {
        throw ReservedOperationInterceptor.notBuilt();
    }

    @Override
    public ResponseEntity<ActivationChange> selectActivationArtifact(
            String activationId,
            String kind,
            String artifactId,
            SelectionRequest selectionRequest,
            @Nullable String ifMatch) {
        throw ReservedOperationInterceptor.notBuilt();
    }

    @Override
    public ResponseEntity<ActivationChange> unselectActivationArtifact(
            String activationId, String kind, String artifactId, @Nullable String ifMatch) {
        throw ReservedOperationInterceptor.notBuilt();
    }
}
