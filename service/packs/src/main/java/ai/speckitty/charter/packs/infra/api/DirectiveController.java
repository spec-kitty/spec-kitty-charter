package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.DirectivesApi;
import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.DirectiveReference;
import ai.speckitty.charter.api.model.DirectiveRegistration;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.application.DirectiveRegistrationService;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The inbound REST adapter for registering a pack's directives under the artifact tree: {@code POST
 * /packs/{packId}/artifacts/directive} and {@code PUT /packs/{packId}/artifacts/directive/{artifactId}} (ADR-0026
 * functional registration; no {@code DELETE}/{@code PATCH}). A directive is read through the generic
 * {@link ArtifactController}; each write answers the written directive in the same {@code Artifact} envelope, with the
 * item read's links, and the path of a 201's {@code Location} is its {@code self}. The writes delegate to
 * {@link DirectiveRegistrationService}. A refusal the services throw is answered by {@link RefusalHandler}; so is a
 * stale {@code If-Match} precondition, as a plain {@code 412} problem (not a refusal code), per ADR-0020.
 *
 * <p>Bodies are the generated contract DTOs only; a storage DSO is never exposed (C-002).
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class DirectiveController implements DirectivesApi {

    private final DirectiveRegistrationService registrations;
    private final Hypermedia links;

    /**
     * A controller over the directive registration use case.
     *
     * @param registrations the registration use case
     * @param links the link builder of the answers
     */
    DirectiveController(DirectiveRegistrationService registrations, Hypermedia links) {
        this.registrations = registrations;
        this.links = links;
    }

    @Override
    public ResponseEntity<Artifact> registerPackDirective(String packId, DirectiveRegistration body) {
        PackIdentifier pack = new PackIdentifier(packId);
        String id = requireId(body.getId());
        ai.speckitty.charter.packs.domain.Directive directive = assemble(
                pack,
                id,
                body.getSchemaVersion(),
                body.getTitle(),
                body.getIntent(),
                body.getEnforcement(),
                body.getScope(),
                body.getProcedures(),
                body.getIntegrityRules(),
                body.getValidationCriteria(),
                body.getExplicitAllowances(),
                body.getReferences());
        List<Relationship> relationships = RelationshipWire.fromWire(body.getRelationships());
        DirectiveRegistrationService.Registered result =
                registrations.registerDirective(pack, directive, relationships, false, null);
        Artifact written = ArtifactDtos.envelope(ArtifactKind.DIRECTIVE, pack, id, links)
                .directive(ArtifactDtos.directive(directive));
        return ResponseEntity.created(ArtifactDtos.location(written))
                .eTag(result.revision().token())
                .body(written);
    }

    @Override
    public ResponseEntity<Artifact> putPackDirective(
            String packId, String artifactId, DirectiveRegistration body, @Nullable String ifMatch) {
        PackIdentifier pack = new PackIdentifier(packId);
        ai.speckitty.charter.packs.domain.Directive directive = assemble(
                pack,
                artifactId,
                body.getSchemaVersion(),
                body.getTitle(),
                body.getIntent(),
                body.getEnforcement(),
                body.getScope(),
                body.getProcedures(),
                body.getIntegrityRules(),
                body.getValidationCriteria(),
                body.getExplicitAllowances(),
                body.getReferences());
        List<Relationship> relationships = RelationshipWire.fromWire(body.getRelationships());
        DirectiveRegistrationService.Registered result =
                registrations.registerDirective(pack, directive, relationships, true, ifMatch);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .eTag(result.revision().token())
                .body(ArtifactDtos.envelope(ArtifactKind.DIRECTIVE, pack, artifactId, links)
                        .directive(ArtifactDtos.directive(directive)));
    }

    // ---- DTO -> domain -------------------------------------------------------------------------------------------

    private static String requireId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED, "A directive registration must carry an id on POST.");
        }
        return id;
    }

    private static ai.speckitty.charter.packs.domain.Directive assemble(
            PackIdentifier pack,
            String id,
            String schemaVersion,
            String title,
            String intent,
            String enforcement,
            @Nullable String scope,
            List<String> procedures,
            List<String> integrityRules,
            List<String> validationCriteria,
            List<String> explicitAllowances,
            List<DirectiveReference> references) {
        try {
            var builder = ai.speckitty.charter.packs.domain.Directive.builder()
                    .id(new DirectiveIdentifier(id))
                    .packId(pack)
                    .schemaVersion(schemaVersion)
                    .title(title)
                    .intent(intent)
                    .enforcement(Enforcement.fromToken(enforcement)
                            .orElseThrow(() -> new CharterRefusal(
                                    RefusalCode.VALIDATION_FAILED, "unknown enforcement: " + enforcement)))
                    .scope(scope);
            procedures.forEach(builder::procedure);
            integrityRules.forEach(builder::integrityRule);
            validationCriteria.forEach(builder::validationCriterion);
            explicitAllowances.forEach(builder::explicitAllowance);
            references.forEach(ref -> builder.reference(new ai.speckitty.charter.packs.domain.DirectiveReference(
                    ref.getType(),
                    ref.getId(),
                    Optional.ofNullable(ref.getWhen()),
                    Optional.ofNullable(ref.getReason()))));
            return builder.build();
        } catch (ConstraintBreachException | IllegalArgumentException invalid) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(invalid.getMessage()), invalid);
        }
    }
}
