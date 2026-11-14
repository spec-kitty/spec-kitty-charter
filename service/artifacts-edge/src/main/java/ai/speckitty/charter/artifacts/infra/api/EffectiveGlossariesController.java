package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.EffectiveGlossariesApi;
import ai.speckitty.charter.api.model.EffectiveGlossaryContextRead;
import ai.speckitty.charter.api.model.EffectiveGlossaryTermRead;
import ai.speckitty.charter.api.model.GlossaryContextList;
import ai.speckitty.charter.artifacts.application.EffectiveGlossaryReads;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The glossary reads under an activation (#106, tag {@code EffectiveGlossaries}):
 * {@code GET /activations/{activationId}/artifacts/glossary}, its item {@code …/glossary/{contextId}} and one term
 * {@code …/glossary/{contextId}/terms/{termId}}, each from the copy the layer merge keeps, whatever its state. The
 * literal paths are preferred to the activation set's templated read of an artifact, which carries no body. A refusal
 * the reads throw is answered by the shared refusal handler; the answers are built as the asset and skill reads under
 * an activation build theirs ({@link EffectiveAssetsController#answer}).
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class EffectiveGlossariesController implements EffectiveGlossariesApi {

    private final EffectiveGlossaryReads glossaries;
    private final EffectiveGlossaryBodies bodies;

    /**
     * A controller over the glossary reads under an activation.
     *
     * @param glossaries the reads
     * @param bodies the wire mapper of the answers and their links
     */
    @Autowired
    public EffectiveGlossariesController(EffectiveGlossaryReads glossaries, EffectiveGlossaryBodies bodies) {
        this.glossaries = Objects.requireNonNull(glossaries, "glossaries");
        this.bodies = Objects.requireNonNull(bodies, "bodies");
    }

    @Override
    public ResponseEntity<GlossaryContextList> listEffectiveGlossaryContexts(
            String activationId, @Nullable String ifNoneMatch) {
        return EffectiveAssetsController.answer(
                glossaries.list(activationId, ifNoneMatch),
                listed -> listed.scope().sourceFingerprint(),
                bodies::list);
    }

    @Override
    public ResponseEntity<EffectiveGlossaryContextRead> getEffectiveGlossaryContext(
            String activationId, String contextId, @Nullable String ifNoneMatch) {
        return EffectiveAssetsController.answer(
                glossaries.context(activationId, contextId, ifNoneMatch),
                read -> read.scope().sourceFingerprint(),
                bodies::context);
    }

    @Override
    public ResponseEntity<EffectiveGlossaryTermRead> getEffectiveGlossaryTerm(
            String activationId, String contextId, String termId, @Nullable String ifNoneMatch) {
        return EffectiveAssetsController.answer(
                glossaries.term(activationId, contextId, termId, ifNoneMatch),
                read -> read.scope().sourceFingerprint(),
                bodies::term);
    }
}
