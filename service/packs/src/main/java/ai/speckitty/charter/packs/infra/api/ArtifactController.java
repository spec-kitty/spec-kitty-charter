package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.ArtifactsApi;
import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.ArtifactPage;
import ai.speckitty.charter.api.model.PageInfo;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.ArtifactPosition;
import ai.speckitty.charter.packs.application.ArtifactReadService;
import ai.speckitty.charter.packs.application.GuideFormat;
import jakarta.validation.ConstraintViolationException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * {@code GET /packs/{packId}/artifacts} and {@code GET /packs/{packId}/artifacts/{kind}/{artifactId}}: the generic
 * reads of {@link ArtifactReadService}, the only reads of a pack's artifacts. The writable kinds are registered under
 * the same tree by their own controllers ({@link DirectiveController} and its siblings). A refusal the service throws is answered by
 * {@link RefusalHandler}. The adapter answers the 400s itself, as plain problems without a code: a kind filter that
 * is not a served kind, a page cursor {@link ArtifactCursor} cannot read, and a parameter out of the bounds the
 * generated interface declares (a page size out of range), which bean validation refuses.
 *
 * <p>Every link is built by {@link Hypermedia}: an item's {@code self} and {@code collection} (no {@code edit} in this
 * minor, R-24), and a page's {@code self} (the request with the query it gave), {@code first} (without the cursor)
 * and, when more artifacts follow, {@code next}.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class ArtifactController implements ArtifactsApi {

    private static final String PACKS = "packs";
    private static final String ARTIFACTS = "artifacts";
    private static final String KIND = "kind";
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
        verified.value().revision().ifPresent(revision -> answer.eTag(revision.token()));
        return answer.body(ArtifactDtos.artifact(
                verified.value(), guides, ArtifactDtos.itemLinks(kind, packId, artifactId, links)));
    }

    /**
     * A parameter that breaks a bound the generated interface declares (a {@code pageSize} of 0 or 201, an empty
     * {@code pageCursor}) is a malformed request: the plain 400 problem, without a code. The interface is
     * {@code @Validated}, so the method validation proxy refuses it before this controller runs, with an exception the
     * shared handler would answer as a 500.
     *
     * @param invalid the violation
     * @return the {@code application/problem+json} 400 response
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> outOfBounds(ConstraintViolationException invalid) {
        return Problems.of(HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY);
    }

    /** The kind filter: a served kind, or a 400 problem without a code. */
    private static Optional<ArtifactKind> filter(@Nullable String kind) {
        if (kind == null) {
            return Optional.empty();
        }
        return Optional.of(ArtifactReadService.servedKind(kind)
                .orElseThrow(() -> new ErrorResponseException(HttpStatus.BAD_REQUEST)));
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
