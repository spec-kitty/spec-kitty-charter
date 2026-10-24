package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.TacticsApi;
import ai.speckitty.charter.api.model.Tactic;
import ai.speckitty.charter.api.model.Tactic1;
import ai.speckitty.charter.api.model.TacticReference;
import ai.speckitty.charter.api.model.TacticStep;
import ai.speckitty.charter.packs.application.TacticReadService;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /packs/{packId}/tactics} and {@code GET /packs/{packId}/tactics/{tacticId}}: a thin projection of
 * {@link TacticReadService} onto the contract's {@code Tactic}. An absent pack or tactic is a refusal the service
 * throws; {@code RefusalHandler} answers it as a 404 problem.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class TacticController implements TacticsApi {

    private final TacticReadService tactics;

    /**
     * A controller over the tactic read service.
     *
     * @param tactics the read use case it answers with
     */
    public TacticController(TacticReadService tactics) {
        this.tactics = tactics;
    }

    @Override
    public ResponseEntity<List<Tactic>> listPackTactics(String packId) {
        var verified = tactics.tacticsOf(new PackIdentifier(packId));
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .body(verified.value().stream().map(TacticController::toDto).toList());
    }

    @Override
    public ResponseEntity<Tactic1> getPackTactic(String packId, String tacticId) {
        var verified = tactics.tactic(new PackIdentifier(packId), new TacticIdentifier(tacticId));
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .body(toSingleDto(verified.value()));
    }

    /**
     * The one-tactic body is a {@code Tactic1}: the generator names the second use of the one {@code Tactic} schema
     * that way, as it does {@code Problem1} (see {@link ProblemJson}). The two classes hold the same members.
     */
    private static Tactic1 toSingleDto(ai.speckitty.charter.packs.domain.Tactic tactic) {
        Tactic listed = toDto(tactic);
        return new Tactic1(
                listed.getId(),
                listed.getSchemaVersion(),
                listed.getName(),
                listed.getPurpose(),
                listed.getNotes(),
                listed.getSteps(),
                listed.getFailureModes(),
                listed.getAppliesToLanguages(),
                listed.getReferences());
    }

    private static Tactic toDto(ai.speckitty.charter.packs.domain.Tactic tactic) {
        return new Tactic(
                tactic.id().value(),
                tactic.schemaVersion(),
                tactic.name(),
                tactic.purpose().orElse(null),
                tactic.notes().orElse(null),
                tactic.steps().stream().map(TacticController::toDto).toList(),
                tactic.failureModes(),
                tactic.appliesToLanguages(),
                tactic.references().stream().map(TacticController::toDto).toList());
    }

    private static TacticStep toDto(ai.speckitty.charter.packs.domain.TacticStep step) {
        return new TacticStep(
                step.title(),
                step.description().orElse(null),
                step.examples(),
                step.references().stream().map(TacticController::toDto).toList());
    }

    private static TacticReference toDto(ai.speckitty.charter.packs.domain.TacticReference reference) {
        return new TacticReference(reference.name(), reference.type(), reference.id(), reference.when());
    }
}
