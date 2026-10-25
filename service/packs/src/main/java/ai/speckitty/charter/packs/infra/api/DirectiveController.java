package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.DirectivesApi;
import ai.speckitty.charter.api.model.Directive;
import ai.speckitty.charter.api.model.Directive1;
import ai.speckitty.charter.api.model.DirectiveReference;
import ai.speckitty.charter.api.model.DirectiveRegistration;
import ai.speckitty.charter.api.model.DirectiveRegistration1;
import ai.speckitty.charter.api.model.Problem;
import ai.speckitty.charter.api.model.RelationshipRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.application.DirectiveReadService;
import ai.speckitty.charter.packs.application.DirectiveRegistrationService;
import ai.speckitty.charter.packs.application.PackWriteStore;
import ai.speckitty.charter.packs.application.PreconditionException;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The inbound REST adapter for a pack's directives: {@code GET}/{@code POST} on the collection and
 * {@code GET}/{@code PUT} on one directive (ADR-0026 functional registration; no {@code DELETE}/{@code PATCH}). The
 * read is a thin projection of {@link DirectiveReadService}; the writes delegate to
 * {@link DirectiveRegistrationService}. A refusal the services throw is answered by {@link RefusalHandler}; a stale
 * {@code If-Match} precondition is answered here as a plain {@code 412} {@link Problem} (not a refusal code), per
 * ADR-0020.
 *
 * <p>Bodies are the generated contract DTOs only; a storage DSO is never exposed (C-002). The one-directive response
 * is a {@code Directive1}, the generator's name for the second use of the one {@code Directive} schema.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class DirectiveController implements DirectivesApi {

    private final DirectiveReadService reads;
    private final DirectiveRegistrationService registrations;
    private final PackWriteStore writeStore;

    /**
     * A controller over the directive read and registration use cases.
     *
     * @param reads the read use case
     * @param registrations the registration use case
     * @param writeStore the write-path seam, consulted for the read {@code ETag} revision
     */
    public DirectiveController(
            DirectiveReadService reads, DirectiveRegistrationService registrations, PackWriteStore writeStore) {
        this.reads = reads;
        this.registrations = registrations;
        this.writeStore = writeStore;
    }

    @Override
    public ResponseEntity<List<Directive>> listPackDirectives(String packId) {
        var verified = reads.directivesOf(new PackIdentifier(packId));
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .body(verified.value().stream().map(DirectiveController::toDto).toList());
    }

    @Override
    public ResponseEntity<Directive1> getPackDirective(String packId, String directiveId) {
        PackIdentifier pack = new PackIdentifier(packId);
        var verified = reads.directive(pack, new DirectiveIdentifier(directiveId));
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .eTag(writeStore.currentRevision(pack).token())
                .body(toSingleDto(verified.value()));
    }

    @Override
    public ResponseEntity<Directive1> registerPackDirective(String packId, DirectiveRegistration body) {
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
        List<Relationship> relationships = relationships(body.getRelationships());
        DirectiveRegistrationService.Registered result =
                registrations.registerDirective(pack, directive, relationships, false, null);
        return ResponseEntity.created(java.net.URI.create("/packs/" + packId + "/directives/" + id))
                .eTag(result.revision().token())
                .body(toSingleDto(directive));
    }

    @Override
    public ResponseEntity<Directive1> putPackDirective(
            String packId, String directiveId, DirectiveRegistration1 body, @Nullable String ifMatch) {
        PackIdentifier pack = new PackIdentifier(packId);
        ai.speckitty.charter.packs.domain.Directive directive = assemble(
                pack,
                directiveId,
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
        List<Relationship> relationships = relationships(body.getRelationships());
        DirectiveRegistrationService.Registered result =
                registrations.registerDirective(pack, directive, relationships, true, unquote(ifMatch));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .eTag(result.revision().token())
                .body(toSingleDto(directive));
    }

    /** A stale {@code If-Match} (412) or a missing one on a sub-resource (428) is a plain {@code Problem}. */
    @ExceptionHandler(PreconditionException.class)
    ResponseEntity<Problem> precondition(PreconditionException failure) {
        HttpStatus status = failure.kind() == PreconditionException.Kind.MISSING
                ? HttpStatus.PRECONDITION_REQUIRED
                : HttpStatus.PRECONDITION_FAILED;
        Problem problem = new Problem(status.getReasonPhrase(), status.value());
        problem.setDetail(failure.getMessage());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /** Strips the optional {@code W/} weak marker and the surrounding quotes of an HTTP entity-tag If-Match value. */
    static @Nullable String unquote(@Nullable String eTag) {
        if (eTag == null) {
            return null;
        }
        String value = eTag.startsWith("W/") ? eTag.substring(2) : eTag;
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
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

    private static List<Relationship> relationships(List<RelationshipRegistration> wire) {
        try {
            return wire.stream().map(DirectiveController::toRelationship).toList();
        } catch (IllegalArgumentException invalid) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(invalid.getMessage()), invalid);
        }
    }

    private static Relationship toRelationship(RelationshipRegistration wire) {
        Urn from = Urn.parse(wire.getFrom())
                .orElseThrow(() -> new IllegalArgumentException("not a URN of a known kind: " + wire.getFrom()));
        Urn to = Urn.parse(wire.getTo())
                .orElseThrow(() -> new IllegalArgumentException("not a URN of a known kind: " + wire.getTo()));
        RelationType type = RelationType.fromToken(wire.getType())
                .orElseThrow(() -> new IllegalArgumentException("unknown relation type: " + wire.getType()));
        return new Relationship(
                from, to, type, Optional.ofNullable(wire.getWhen()), Optional.ofNullable(wire.getRationale()));
    }

    // ---- domain -> DTO -------------------------------------------------------------------------------------------

    private static Directive1 toSingleDto(ai.speckitty.charter.packs.domain.Directive directive) {
        Directive listed = toDto(directive);
        return new Directive1(
                listed.getId(),
                listed.getSchemaVersion(),
                listed.getTitle(),
                listed.getIntent(),
                listed.getEnforcement(),
                listed.getScope(),
                listed.getProcedures(),
                listed.getIntegrityRules(),
                listed.getValidationCriteria(),
                listed.getExplicitAllowances(),
                listed.getReferences(),
                listed.getRelationships());
    }

    private static Directive toDto(ai.speckitty.charter.packs.domain.Directive directive) {
        return new Directive(
                directive.id().value(),
                directive.schemaVersion(),
                directive.title(),
                directive.intent(),
                directive.enforcement().token(),
                directive.scope().orElse(null),
                directive.procedures(),
                directive.integrityRules(),
                directive.validationCriteria(),
                directive.explicitAllowances(),
                directive.references().stream().map(DirectiveController::toDto).toList(),
                directive.relationships().stream()
                        .map(DirectiveController::toDto)
                        .toList());
    }

    private static DirectiveReference toDto(ai.speckitty.charter.packs.domain.DirectiveReference reference) {
        return new DirectiveReference(
                reference.type(),
                reference.id(),
                reference.when().orElse(null),
                reference.reason().orElse(null));
    }

    private static RelationshipRegistration toDto(Relationship relationship) {
        RelationshipRegistration dto = new RelationshipRegistration(
                relationship.from().toString(),
                relationship.to().toString(),
                relationship.type().token());
        dto.setRationale(relationship.rationale().orElse(null));
        dto.setWhen(relationship.when().orElse(null));
        return dto;
    }
}
