package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.TacticsApi;
import ai.speckitty.charter.api.model.Tactic;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.TacticReadService;
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
                .body(verified.value().stream().map(ArtifactDtos::tactic).toList());
    }

    @Override
    public ResponseEntity<Tactic> getPackTactic(String packId, String tacticId) {
        var verified = tactics.tactic(new PackIdentifier(packId), new TacticIdentifier(tacticId));
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .body(ArtifactDtos.tactic(verified.value()));
    }
}
