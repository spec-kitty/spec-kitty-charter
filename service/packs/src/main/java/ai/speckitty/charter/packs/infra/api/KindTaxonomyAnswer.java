package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.EndpointPair;
import ai.speckitty.charter.api.model.KindRow;
import ai.speckitty.charter.api.model.KindTaxonomy;
import ai.speckitty.charter.api.model.RelationRow;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.application.KindTaxonomyReadService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The answer of {@code GET /offering/kinds}: the kind taxonomy of {@link KindTaxonomyReadService} as the contract's
 * {@code KindTaxonomy}, and its strong entity tag, the SHA-256 digest of the JSON body. The body is written with the
 * application's JSON mapper, the one the message converter writes it with, so the digest is the digest of the bytes
 * served. Both are built once: the taxonomy is the model's and never changes while the service runs.
 */
@Component
final class KindTaxonomyAnswer {

    private final KindTaxonomy body;
    private final StrongETag tag;

    /**
     * The answer, built from the taxonomy.
     *
     * @param reads the taxonomy read
     * @param links the link builder
     * @param json the application's JSON mapper
     */
    KindTaxonomyAnswer(KindTaxonomyReadService reads, Hypermedia links, JsonMapper json) {
        this.body = body(reads.taxonomy(), links);
        this.tag = StrongETag.of(json.writeValueAsBytes(body));
    }

    /**
     * The body.
     *
     * @return the taxonomy as the contract shapes it
     */
    KindTaxonomy body() {
        return body;
    }

    /**
     * The entity tag of the body.
     *
     * @return its tag
     */
    StrongETag tag() {
        return tag;
    }

    private static KindTaxonomy body(KindTaxonomyReadService.Taxonomy taxonomy, Hypermedia links) {
        List<KindRow> kinds =
                taxonomy.kinds().stream().map(kind -> row(kind, links)).toList();
        List<RelationRow> relations =
                taxonomy.relations().stream().map(KindTaxonomyAnswer::row).toList();
        Map<Relation, Hypermedia.LinkValue> own = new EnumMap<>(Relation.class);
        own.put(Relation.SELF, links.link("offering", "kinds"));
        own.put(Relation.OFFERING, links.link("offering"));
        return new KindTaxonomy(kinds, relations, Hypermedia.wire(own));
    }

    private static KindRow row(KindTaxonomyReadService.KindFacts kind, Hypermedia links) {
        Map<Relation, Hypermedia.LinkValue> rowLinks = new EnumMap<>(Relation.class);
        if (kind.listed()) {
            rowLinks.put(
                    Relation.ARTIFACTS,
                    links.templated(
                            Map.of("kind", kind.artifactKind().orElseThrow().token()),
                            "packs",
                            "{packId}",
                            "artifacts"));
        }
        KindRow row = new KindRow(
                KindRow.KindEnum.fromValue(kind.kind().token()),
                kind.artifact(),
                kind.activatable(),
                kind.declarableInGraphs(),
                kind.edgeTarget(),
                Hypermedia.wire(rowLinks));
        kind.plural().ifPresent(row::setPlural);
        kind.artifactKind().map(ArtifactKind::token).ifPresent(row::setArtifactKind);
        return row;
    }

    private static RelationRow row(KindTaxonomyReadService.RelationFacts relation) {
        return new RelationRow(
                RelationRow.RelationEnum.fromValue(relation.relation()),
                RelationRow.HeldAsEnum.fromValue(relation.heldAs().token()),
                relation.directed(),
                relation.derived(),
                relation.endpoints().stream()
                        .map(pair -> new EndpointPair(
                                EndpointPair.SourceEnum.fromValue(pair.source().token()),
                                EndpointPair.TargetEnum.fromValue(pair.target().token())))
                        .toList());
    }
}
