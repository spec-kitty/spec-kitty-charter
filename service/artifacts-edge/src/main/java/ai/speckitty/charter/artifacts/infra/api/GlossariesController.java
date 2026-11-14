package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.GlossariesApi;
import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.GlossaryContextList;
import ai.speckitty.charter.api.model.GlossaryTerm;
import ai.speckitty.charter.artifacts.application.GlossaryReads;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.web.DeferredETag;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The glossary reads of a pack (#106, tag {@code Glossaries}): {@code GET /packs/{packId}/artifacts/glossary}, its item
 * {@code …/glossary/{contextId}} and one term {@code …/glossary/{contextId}/terms/{termId}}. A refusal the reads throw
 * is answered by the shared refusal handler.
 *
 * <p>Each answer carries the pack revision as its {@code ETag} and the fingerprint the one verified read agreed on. None
 * takes {@code If-None-Match}; the tag is handed to the web transport's {@link DeferredETag} so that Spring's own
 * conditional check never answers a {@code 304} the contract does not declare.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class GlossariesController implements GlossariesApi {

    private final GlossaryReads glossaries;
    private final GlossaryBodies bodies;

    /**
     * A controller over the glossary reads.
     *
     * @param glossaries the reads
     * @param bodies the wire mapper of a context, a term and their links
     */
    @Autowired
    public GlossariesController(GlossaryReads glossaries, GlossaryBodies bodies) {
        this.glossaries = Objects.requireNonNull(glossaries, "glossaries");
        this.bodies = Objects.requireNonNull(bodies, "bodies");
    }

    @Override
    public ResponseEntity<GlossaryContextList> listPackGlossaryContexts(String packId) {
        GlossaryReads.Listed listed = glossaries.list(new PackIdentifier(packId));
        DeferredETag.offer(EntityTags.quote(listed.revision()));
        return ResponseEntity.ok()
                .header(HeaderNames.SOURCE_FINGERPRINT, listed.sourceFingerprint())
                .body(bodies.list(packId, listed.contexts()));
    }

    @Override
    public ResponseEntity<Artifact> getPackGlossaryContext(String packId, String contextId) {
        GlossaryReads.Read read = glossaries.context(new PackIdentifier(packId), contextId);
        DeferredETag.offer(EntityTags.quote(read.revision()));
        return ResponseEntity.ok()
                .header(HeaderNames.SOURCE_FINGERPRINT, read.sourceFingerprint())
                .body(bodies.artifact(read.context()));
    }

    @Override
    public ResponseEntity<GlossaryTerm> getPackGlossaryTerm(String packId, String contextId, String termId) {
        GlossaryReads.TermRead read = glossaries.term(new PackIdentifier(packId), contextId, termId);
        DeferredETag.offer(EntityTags.quote(read.revision()));
        return ResponseEntity.ok()
                .header(HeaderNames.SOURCE_FINGERPRINT, read.sourceFingerprint())
                .body(bodies.term(read));
    }
}
