package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.ArtifactsApi;
import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.ArtifactPage;
import ai.speckitty.charter.api.model.ArtifactReferenceList;
import ai.speckitty.charter.api.model.HoldingPack;
import ai.speckitty.charter.api.model.PageInfo;
import ai.speckitty.charter.api.model.ReferencedArtifact;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.ArtifactPosition;
import ai.speckitty.charter.packs.application.ArtifactReadService;
import ai.speckitty.charter.packs.application.GuideFormat;
import ai.speckitty.charter.web.InvalidParameter;
import ai.speckitty.charter.web.RefusalHandler;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * {@code GET /packs/{packId}/artifacts}, {@code GET /packs/{packId}/artifacts/{kind}/{artifactId}} and the composed
 * {@code GET /packs/{packId}/artifacts/{kind}/{artifactId}/references}: the generic reads of
 * {@link ArtifactReadService}, the only reads of a pack's artifacts. The writable kinds are registered under
 * the same tree by their own controllers ({@link DirectiveController} and its siblings). A refusal the service throws is answered by
 * {@link RefusalHandler}. The adapter answers the 400s itself, as plain problems without a code: a kind filter that
 * is not a listed kind, a page cursor {@link ArtifactCursor} cannot read, and a parameter out of the bounds the
 * generated interface declares (a page size out of range), which bean validation refuses.
 *
 * <p>Every link is built by {@link Hypermedia}: an item's {@code self} and {@code collection} (no {@code edit} in this
 * minor, R-24), with a step contract's {@code mission-type} and {@code step} when its pack holds them, and a page's
 * {@code self} (the request with the query it gave), {@code first} (without the cursor) and, when more artifacts follow,
 * {@code next}.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class ArtifactController implements ArtifactsApi {

    private static final String PACKS = "packs";
    private static final String ARTIFACTS = "artifacts";
    private static final String KIND = "kind";
    private static final String REFERENCES = "references";
    private static final String PAGE_SIZE = "pageSize";
    private static final String PAGE_CURSOR = "pageCursor";

    private final ArtifactReadService reads;
    private final Hypermedia links;
    private final GuideFormat guides;

    /**
     * A controller over the generic read use case.
     *
     * @param reads the generic reads
     * @param links the link builder
     * @param guides the guide format a toolguide's guide is written in
     */
    ArtifactController(ArtifactReadService reads, Hypermedia links, GuideFormat guides) {
        this.reads = reads;
        this.links = links;
        this.guides = guides;
    }

    @Override
    public ResponseEntity<ArtifactPage> listPackArtifacts(
            String packId, @Nullable String kind, Integer pageSize, @Nullable String pageCursor) {
        Optional<ArtifactKind> filter = filter(kind);
        Optional<ArtifactPosition> after =
                Optional.ofNullable(pageCursor).map(cursor -> ArtifactCursor.decode(cursor, filter));
        var verified = reads.list(new PackIdentifier(packId), filter, after, pageSize);
        ArtifactReadService.Page page = verified.value();
        Optional<String> next = page.hasMore()
                ? Optional.of(ArtifactCursor.encode(page.items().getLast().position()))
                : Optional.empty();
        Map<String, String> query = query(filter, pageSize);
        Map<Relation, Hypermedia.LinkValue> pageLinks = new EnumMap<>(Relation.class);
        pageLinks.put(
                Relation.SELF,
                links.link(withCursor(query, Optional.ofNullable(pageCursor)), PACKS, packId, ARTIFACTS));
        pageLinks.put(Relation.FIRST, links.link(query, PACKS, packId, ARTIFACTS));
        next.ifPresent(cursor -> pageLinks.put(
                Relation.NEXT, links.link(withCursor(query, Optional.of(cursor)), PACKS, packId, ARTIFACTS)));
        List<ai.speckitty.charter.api.model.ArtifactSummary> items = page.items().stream()
                .map(summary -> ArtifactDtos.summary(
                        summary, Hypermedia.wire(Map.of(Relation.SELF, itemLink(packId, summary.position())))))
                .toList();
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .body(new ArtifactPage(
                        items, new PageInfo(page.hasMore(), next.orElse(null), pageSize), Hypermedia.wire(pageLinks)));
    }

    @Override
    public ResponseEntity<Artifact> getPackArtifact(String packId, String kind, String artifactId) {
        var verified = reads.item(new PackIdentifier(packId), kind, artifactId);
        ResponseEntity.BodyBuilder answer =
                ResponseEntity.ok().headers(CharterHeaders.fingerprint(verified.fingerprint()));
        answer.eTag(verified.value().revision().token());
        return answer.body(ArtifactDtos.artifact(
                verified.value(),
                guides,
                ArtifactDtos.itemLinks(verified.value(), kind, packId, artifactId, links),
                links));
    }

    @Override
    public ResponseEntity<ArtifactReferenceList> listArtifactReferences(String packId, String kind, String artifactId) {
        var verified = reads.references(new PackIdentifier(packId), kind, artifactId);
        List<ReferencedArtifact> items = verified.value().stream()
                .map(reference -> referenced(reference))
                .toList();
        Map<Relation, Hypermedia.LinkValue> own = new EnumMap<>(Relation.class);
        own.put(Relation.SELF, links.link(PACKS, packId, ARTIFACTS, kind, artifactId, REFERENCES));
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .body(new ArtifactReferenceList(items, Hypermedia.wire(own)));
    }

    /** One resolved reference on the wire: the artifact link only when the target is a kind the service lists. */
    private ReferencedArtifact referenced(ArtifactReadService.Reference reference) {
        Map<Relation, Hypermedia.LinkValue> target = new EnumMap<>(Relation.class);
        reference
                .holder()
                .ifPresent(holder -> reference
                        .urn()
                        .kind()
                        .artifactKind()
                        .filter(kind -> ArtifactReadService.listed().contains(kind))
                        .ifPresent(kind -> target.put(
                                Relation.ARTIFACT,
                                links.link(
                                        PACKS,
                                        holder.configured().id().value(),
                                        ARTIFACTS,
                                        kind.token(),
                                        reference.urn().id()))));
        ReferencedArtifact wire = new ReferencedArtifact(
                reference.written().toString(),
                ReferencedArtifact.RelationEnum.fromValue(reference.relation().token()),
                reference.urn().toString(),
                Hypermedia.wire(target));
        reference
                .holder()
                .ifPresent(holder -> wire.setHoldingPack(new HoldingPack(
                        holder.configured().id().value(),
                        holder.descriptor().packId(),
                        holder.descriptor().name())));
        return wire;
    }

    /** The kind filter: a listed kind, or a 400 problem without a code whose detail lists the listed kinds (#411). */
    private static Optional<ArtifactKind> filter(@Nullable String kind) {
        if (kind == null) {
            return Optional.empty();
        }
        return Optional.of(ArtifactReadService.listedKind(kind)
                .orElseThrow(() -> new InvalidParameter(
                        InvalidParameter.In.QUERY,
                        "kind",
                        kind,
                        InvalidParameter.oneOf(ArtifactReadService.LISTED.stream()
                                .map(ArtifactKind::token)
                                .toList()))));
    }

    /**
     * The query a page's links keep: the kind filter and the page size, each only when the request gave it, in that
     * order. The page size has a default, so whether the request gave it is read from the request itself.
     */
    private static Map<String, String> query(Optional<ArtifactKind> filter, int pageSize) {
        Map<String, String> query = new LinkedHashMap<>();
        filter.ifPresent(kind -> query.put(KIND, kind.token()));
        if (ServletUriComponentsBuilder.fromCurrentRequest()
                .build()
                .getQueryParams()
                .containsKey(PAGE_SIZE)) {
            query.put(PAGE_SIZE, Integer.toString(pageSize));
        }
        return query;
    }

    private static Map<String, String> withCursor(Map<String, String> query, Optional<String> cursor) {
        Map<String, String> with = new LinkedHashMap<>(query);
        cursor.ifPresent(value -> with.put(PAGE_CURSOR, value));
        return with;
    }

    private Hypermedia.LinkValue itemLink(String packId, ArtifactPosition position) {
        return links.link(PACKS, packId, ARTIFACTS, position.kind().token(), position.id());
    }
}
