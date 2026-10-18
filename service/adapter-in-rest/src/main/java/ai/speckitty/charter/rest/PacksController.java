package ai.speckitty.charter.rest;

import ai.speckitty.charter.api.PacksApi;
import ai.speckitty.charter.api.model.Diagnostic;
import ai.speckitty.charter.api.model.PackListing;
import ai.speckitty.charter.api.model.PackSummary;
import ai.speckitty.charter.read.PackListingQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /packs}. */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class PacksController implements PacksApi {

    private final PackListingQuery query;

    public PacksController(PackListingQuery query) {
        this.query = query;
    }

    @Override
    public ResponseEntity<PackListing> listPacks() {
        return ResponseEntity.ok(new PackListing(query.answer().stream().map(PacksController::toDto).toList()));
    }

    private static PackSummary toDto(ai.speckitty.charter.read.PackSummary pack) {
        return new PackSummary(pack.name(), PackSummary.LevelEnum.fromValue(pack.level().token()),
                pack.packId().orElse(null), pack.packVersion().orElse(null), pack.parentPack().orElse(null),
                pack.nodeCount(), pack.edgeCount(),
                pack.diagnostics().stream()
                        .map(d -> new Diagnostic(Diagnostic.CodeEnum.fromValue(d.code().name()), d.detail()))
                        .toList());
    }
}
