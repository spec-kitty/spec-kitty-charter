package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.OfferingApi;
import ai.speckitty.charter.api.model.KindTaxonomy;
import ai.speckitty.charter.api.model.Offering;
import ai.speckitty.charter.api.model.OfferingPack;
import ai.speckitty.charter.api.model.ResolvedDependency;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.web.RefusalHandler;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /offering}: the configured packs of {@link OfferingReadService}, in its order, each with the link to its
 * artifacts, and the fingerprint the read verified; and {@code GET /offering/kinds}, the kind taxonomy of
 * {@link KindTaxonomyAnswer}, one controller because the contract tags both {@code Offering}. A refusal the service
 * throws is answered by {@link RefusalHandler}. Every link is built by {@link Hypermedia}: the answer's {@code self}
 * and each pack's {@code artifacts}; a pack has no {@code self}, since reading one pack is not part of this version.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class OfferingController implements OfferingApi {

    private final OfferingReadService reads;
    private final KindTaxonomyAnswer kinds;
    private final Hypermedia links;

    /**
     * A controller over the offering use case and the kind taxonomy.
     *
     * @param reads the offering read
     * @param kinds the kind taxonomy's answer
     * @param links the link builder
     */
    OfferingController(OfferingReadService reads, KindTaxonomyAnswer kinds, Hypermedia links) {
        this.reads = reads;
        this.kinds = kinds;
        this.links = links;
    }

    @Override
    public ResponseEntity<Offering> getOffering() {
        var verified = reads.offering();
        List<OfferingPack> packs = verified.value().stream().map(this::pack).toList();
        Map<Relation, Hypermedia.LinkValue> own = new EnumMap<>(Relation.class);
        own.put(Relation.SELF, links.link("offering"));
        own.put(Relation.KINDS, links.link("offering", "kinds"));
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .body(new Offering(packs, Hypermedia.wire(own)));
    }

    /**
     * The kind taxonomy, or {@code 304} when the client holds it. The guard has already refused a browser request or a
     * missing token, so {@code If-None-Match} is read only here. The {@code 200} carries no {@code ETag} on the entity:
     * {@link KindTaxonomyETag} adds it when the body is written, after Spring's own conditional check (see
     * {@link ContractDocumentETag}). The {@code 304} carries it here. Neither carries
     * {@code Charter-Source-Fingerprint}: no pack file is read.
     *
     * @param ifNoneMatch the entity tags the client holds, if any
     * @return {@code 304} with the ETag, or {@code 200} with the taxonomy
     */
    @Override
    public ResponseEntity<KindTaxonomy> getKindTaxonomy(@Nullable String ifNoneMatch) {
        if (kinds.tag().matchedBy(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(kinds.tag().etag())
                    .build();
        }
        return ResponseEntity.ok(kinds.body());
    }

    private OfferingPack pack(OfferingReadService.OfferingEntry entry) {
        String id = entry.id().value();
        return new OfferingPack(
                id,
                entry.descriptorPackId().value(),
                OfferingPack.LevelEnum.fromValue(entry.level().token()),
                entry.name(),
                entry.version(),
                entry.dependencies().stream()
                        .map(edge -> new ResolvedDependency(edge.to(), edge.range(), edge.version()))
                        .toList(),
                Hypermedia.wire(Map.of(Relation.ARTIFACTS, links.link("packs", id, "artifacts"))));
    }
}
