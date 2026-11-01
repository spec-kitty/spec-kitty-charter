package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.ParadigmsApi;
import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.ParadigmReference;
import ai.speckitty.charter.api.model.ParadigmRegistration;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.application.ParadigmRegistrationService;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The inbound REST adapter for registering a pack's paradigms under the artifact tree: {@code POST
 * /packs/{packId}/artifacts/paradigm} and {@code PUT /packs/{packId}/artifacts/paradigm/{artifactId}} (ADR-0026
 * functional registration; no {@code DELETE}/{@code PATCH}). It is a faithful copy of {@link DirectiveController},
 * kept independent per-kind. A paradigm is read through the generic {@link ArtifactController}; each write answers the
 * written paradigm in the same {@code Artifact} envelope. The writes delegate to {@link ParadigmRegistrationService}. A refusal the services
 * throw is answered by {@link RefusalHandler}; so is a stale {@code If-Match}
 * precondition, as a plain {@code 412} problem (not a refusal code), per ADR-0020.
 *
 * <p>Bodies are the generated contract DTOs only; a storage DSO is never exposed (C-002).
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class ParadigmController implements ParadigmsApi {

    private final ParadigmRegistrationService registrations;
    private final Hypermedia links;

    /**
     * A controller over the paradigm registration use case.
     *
     * @param registrations the registration use case
     * @param links the link builder of the answers
     */
    ParadigmController(ParadigmRegistrationService registrations, Hypermedia links) {
        this.registrations = registrations;
        this.links = links;
    }

    @Override
    public ResponseEntity<Artifact> registerPackParadigm(String packId, ParadigmRegistration body) {
        PackIdentifier pack = new PackIdentifier(packId);
        String id = requireId(body.getId());
        ai.speckitty.charter.packs.domain.Paradigm paradigm = assemble(
                pack,
                id,
                body.getSchemaVersion(),
                body.getName(),
                body.getSummary(),
                OptionalLists.orEmpty(body.getDirectiveRefs()),
                OptionalLists.orEmpty(body.getReferences()));
        List<Relationship> relationships = RelationshipWire.fromWire(OptionalLists.orEmpty(body.getRelationships()));
        ParadigmRegistrationService.Registered result =
                registrations.registerParadigm(pack, paradigm, relationships, false, null);
        Artifact written =
                ArtifactDtos.envelope(ArtifactKind.PARADIGM, pack, id, links).paradigm(ArtifactDtos.paradigm(paradigm));
        return ResponseEntity.created(ArtifactDtos.location(written))
                .eTag(result.revision().token())
                .body(written);
    }

    @Override
    public ResponseEntity<Artifact> putPackParadigm(
            String packId, String artifactId, ParadigmRegistration body, @Nullable String ifMatch) {
        PackIdentifier pack = new PackIdentifier(packId);
        ai.speckitty.charter.packs.domain.Paradigm paradigm = assemble(
                pack,
                artifactId,
                body.getSchemaVersion(),
                body.getName(),
                body.getSummary(),
                OptionalLists.orEmpty(body.getDirectiveRefs()),
                OptionalLists.orEmpty(body.getReferences()));
        List<Relationship> relationships = RelationshipWire.fromWire(OptionalLists.orEmpty(body.getRelationships()));
        ParadigmRegistrationService.Registered result =
                registrations.registerParadigm(pack, paradigm, relationships, true, ifMatch);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .eTag(result.revision().token())
                .body(ArtifactDtos.envelope(ArtifactKind.PARADIGM, pack, artifactId, links)
                        .paradigm(ArtifactDtos.paradigm(paradigm)));
    }

    // ---- DTO -> domain -------------------------------------------------------------------------------------------

    private static String requireId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED, "A paradigm registration must carry an id on POST.");
        }
        return id;
    }

    private static ai.speckitty.charter.packs.domain.Paradigm assemble(
            PackIdentifier pack,
            String id,
            String schemaVersion,
            String name,
            String summary,
            List<String> directiveRefs,
            List<ParadigmReference> references) {
        try {
            var builder = ai.speckitty.charter.packs.domain.Paradigm.builder()
                    .id(new ParadigmIdentifier(id))
                    .packId(pack)
                    .schemaVersion(schemaVersion)
                    .name(name)
                    .summary(summary);
            directiveRefs.forEach(builder::directiveRef);
            references.forEach(ref -> builder.reference(
                    new ai.speckitty.charter.packs.domain.ParadigmReference(ref.getType(), ref.getId())));
            return builder.build();
        } catch (ConstraintBreachException | IllegalArgumentException invalid) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(invalid.getMessage()), invalid);
        }
    }
}
