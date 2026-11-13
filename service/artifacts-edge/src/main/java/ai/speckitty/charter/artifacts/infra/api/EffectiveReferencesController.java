package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.EffectiveReferencesApi;
import ai.speckitty.charter.api.model.ArtifactReferenceList;
import ai.speckitty.charter.artifacts.application.EffectiveReferenceReads;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The references of an artifact under an activation (#532, tag {@code EffectiveReferences}):
 * {@code GET /activations/{activationId}/artifacts/{kind}/{artifactId}/references}, the pack read of the references of
 * the pack that holds the version in force, with each target the closure holds linked under the activation. As the pack
 * read, it has no {@code ETag} and answers the source fingerprint of the read. A refusal the read throws is answered by
 * the shared refusal handler.
 *
 * <p>No literal path of the activation tree ends in {@code /references}, so the template is the route of every listed
 * kind, an asset and a skill included: {@code …/asset/{artifactId}/references} is the references of the asset, never
 * its payload or its item.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class EffectiveReferencesController implements EffectiveReferencesApi {

    private final EffectiveReferenceReads references;
    private final EffectiveBodies bodies;

    /**
     * A controller over the references read under an activation.
     *
     * @param references the read
     * @param bodies the wire mapper of the answer and its links
     */
    @Autowired
    public EffectiveReferencesController(EffectiveReferenceReads references, EffectiveBodies bodies) {
        this.references = Objects.requireNonNull(references, "references");
        this.bodies = Objects.requireNonNull(bodies, "bodies");
    }

    @Override
    public ResponseEntity<ArtifactReferenceList> listEffectiveArtifactReferences(
            String activationId, String kind, String artifactId) {
        EffectiveReferenceReads.Read read = references.references(activationId, kind, artifactId);
        return ResponseEntity.ok()
                .header(HeaderNames.SOURCE_FINGERPRINT, read.scope().sourceFingerprint())
                .body(bodies.references(read));
    }
}
