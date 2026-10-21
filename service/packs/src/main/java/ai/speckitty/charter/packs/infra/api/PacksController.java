package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.PacksApi;
import ai.speckitty.charter.api.model.Diagnostic;
import ai.speckitty.charter.api.model.PackListing;
import ai.speckitty.charter.api.model.PackListing1;
import ai.speckitty.charter.api.model.PackSummary;
import ai.speckitty.charter.packs.application.PackListingQuery;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code GET /packs} and {@code POST /packs/refresh}.
 *
 * <p>A refresh forces the service to re-read every configured pack and the project configuration, and to discard any
 * built read model, then answers the refreshed listing. This version keeps no built graph, so it re-reads and
 * re-verifies: it is the same two-digest consistent load as the listing, through {@link PackListingQuery}, which
 * loads afresh for every answer. Once the service keeps a built graph (issue #73), a refresh rebuilds it. It changes
 * nothing on disk and is idempotent. It takes no request body, so that an optional paths hint can be added later
 * without breaking a client: a non-empty body is a 400, checked before anything is read, and never parsed.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class PacksController implements PacksApi {

    private final PackListingQuery query;
    private final HttpServletRequest request;

    /**
     * A controller over the pack listing.
     *
     * @param query the read use case it answers with
     * @param request the request being answered, which Spring injects as a proxy bound to the current one
     */
    public PacksController(PackListingQuery query, HttpServletRequest request) {
        this.query = query;
        this.request = request;
    }

    @Override
    public ResponseEntity<PackListing> listPacks() {
        ai.speckitty.charter.packs.application.PackListing listing = query.answer();
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(listing.sourceFingerprint()))
                .body(new PackListing(summaries(listing)));
    }

    /**
     * The refreshed listing. Its body is a {@code PackListing1}: the generator names the second operation's copy of the
     * one {@code PackListing} schema that way, as it does {@code Problem1} (see {@link ProblemJson}). The two classes
     * hold the same members.
     */
    @Override
    public ResponseEntity<PackListing1> refreshPacks() {
        rejectBody();
        ai.speckitty.charter.packs.application.PackListing listing = query.answer();
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(listing.sourceFingerprint()))
                .body(new PackListing1(summaries(listing)));
    }

    private static List<PackSummary> summaries(ai.speckitty.charter.packs.application.PackListing listing) {
        return listing.packs().stream().map(PacksController::toDto).toList();
    }

    /** A body is told by its length, or, when the length is not known, by a first byte; it is never parsed. */
    private void rejectBody() {
        try {
            long length = request.getContentLengthLong();
            if (length > 0 || (length < 0 && request.getInputStream().read() != -1)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static PackSummary toDto(ai.speckitty.charter.packs.application.PackSummary pack) {
        return new PackSummary(
                pack.name(),
                PackSummary.LevelEnum.fromValue(pack.level().token()),
                Absent.asNull(pack.packId()),
                Absent.asNull(pack.packVersion()),
                Absent.asNull(pack.parentPack()),
                pack.nodeCount(),
                pack.edgeCount(),
                pack.diagnostics().stream()
                        .map(d -> new Diagnostic(
                                Diagnostic.CodeEnum.fromValue(d.code().token()), d.detail()))
                        .toList());
    }
}
