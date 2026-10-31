package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.OfferingApi;
import ai.speckitty.charter.api.model.Offering;
import ai.speckitty.charter.api.model.OfferingPack;
import ai.speckitty.charter.packs.application.OfferingReadService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /offering}: the configured packs of {@link OfferingReadService}, in its order, each with the link to its
 * artifacts, and the fingerprint the read verified. A refusal the service throws is answered by {@link RefusalHandler}.
 * Every link is built by {@link Hypermedia}: the answer's {@code self} and each pack's {@code artifacts}; a pack has no
 * {@code self}, since reading one pack is not part of this version.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class OfferingController implements OfferingApi {

    private final OfferingReadService reads;
    private final Hypermedia links;

    /**
     * A controller over the offering use case.
     *
     * @param reads the offering read
     * @param links the link builder
     */
    OfferingController(OfferingReadService reads, Hypermedia links) {
        this.reads = reads;
        this.links = links;
    }

    @Override
    public ResponseEntity<Offering> getOffering() {
        var verified = reads.offering();
        List<OfferingPack> packs = verified.value().stream().map(this::pack).toList();
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .body(new Offering(packs, Hypermedia.wire(Map.of(Relation.SELF, links.link("offering")))));
    }

    private OfferingPack pack(OfferingReadService.OfferingEntry entry) {
        String id = entry.id().value();
        return new OfferingPack(
                id,
                OfferingPack.LevelEnum.fromValue(entry.level().token()),
                entry.name(),
                entry.version(),
                Hypermedia.wire(Map.of(Relation.ARTIFACTS, links.link("packs", id, "artifacts"))));
    }
}
