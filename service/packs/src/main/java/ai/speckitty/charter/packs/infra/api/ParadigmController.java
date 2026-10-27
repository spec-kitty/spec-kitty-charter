package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.ParadigmsApi;
import ai.speckitty.charter.api.model.Paradigm;
import ai.speckitty.charter.api.model.ParadigmReference;
import ai.speckitty.charter.api.model.ParadigmRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.application.ParadigmReadService;
import ai.speckitty.charter.packs.application.ParadigmRegistrationService;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The inbound REST adapter for a pack's paradigms: {@code GET}/{@code POST} on the collection and
 * {@code GET}/{@code PUT} on one paradigm (ADR-0026 functional registration; no {@code DELETE}/{@code PATCH}). It is a
 * faithful copy of {@link DirectiveController}, kept independent per-kind. The read is a thin projection of
 * {@link ParadigmReadService}; the writes delegate to {@link ParadigmRegistrationService}. A refusal the services
 * throw is answered by {@link RefusalHandler}; so is a stale {@code If-Match}
 * precondition, as a plain {@code 412} problem (not a refusal code), per ADR-0020.
 *
 * <p>Bodies are the generated contract DTOs only; a storage DSO is never exposed (C-002).
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class ParadigmController implements ParadigmsApi {

    private final ParadigmReadService reads;
    private final ParadigmRegistrationService registrations;

    /**
     * A controller over the paradigm read and registration use cases.
     *
     * @param reads the read use case
     * @param registrations the registration use case
     */
    public ParadigmController(ParadigmReadService reads, ParadigmRegistrationService registrations) {
        this.reads = reads;
        this.registrations = registrations;
    }

    @Override
    public ResponseEntity<List<Paradigm>> listPackParadigms(String packId) {
        var verified = reads.paradigmsOf(new PackIdentifier(packId));
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .body(verified.value().stream().map(ParadigmController::toDto).toList());
    }

    @Override
    public ResponseEntity<Paradigm> getPackParadigm(String packId, String paradigmId) {
        var verified = reads.paradigmWithRevision(new PackIdentifier(packId), new ParadigmIdentifier(paradigmId));
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .eTag(verified.value().revision().token())
                .body(toDto(verified.value().value()));
    }

    @Override
    public ResponseEntity<Paradigm> registerPackParadigm(String packId, ParadigmRegistration body) {
        PackIdentifier pack = new PackIdentifier(packId);
        String id = requireId(body.getId());
        ai.speckitty.charter.packs.domain.Paradigm paradigm = assemble(
                pack,
                id,
                body.getSchemaVersion(),
                body.getName(),
                body.getSummary(),
                body.getDirectiveRefs(),
                body.getReferences());
        List<Relationship> relationships = RelationshipWire.fromWire(body.getRelationships());
        ParadigmRegistrationService.Registered result =
                registrations.registerParadigm(pack, paradigm, relationships, false, null);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri()
                        .path("/{id}")
                        .buildAndExpand(id)
                        .toUri())
                .eTag(result.revision().token())
                .body(toDto(paradigm));
    }

    @Override
    public ResponseEntity<Paradigm> putPackParadigm(
            String packId, String paradigmId, ParadigmRegistration body, @Nullable String ifMatch) {
        PackIdentifier pack = new PackIdentifier(packId);
        ai.speckitty.charter.packs.domain.Paradigm paradigm = assemble(
                pack,
                paradigmId,
                body.getSchemaVersion(),
                body.getName(),
                body.getSummary(),
                body.getDirectiveRefs(),
                body.getReferences());
        List<Relationship> relationships = RelationshipWire.fromWire(body.getRelationships());
        ParadigmRegistrationService.Registered result =
                registrations.registerParadigm(pack, paradigm, relationships, true, ifMatch);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .eTag(result.revision().token())
                .body(toDto(paradigm));
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

    // ---- domain -> DTO -------------------------------------------------------------------------------------------

    private static Paradigm toDto(ai.speckitty.charter.packs.domain.Paradigm paradigm) {
        return new Paradigm(
                paradigm.id().value(),
                paradigm.schemaVersion().orElseThrow(),
                paradigm.name(),
                paradigm.summary(),
                paradigm.directiveRefs(),
                paradigm.references().stream().map(ParadigmController::toDto).toList(),
                paradigm.relationships().stream().map(RelationshipWire::toWire).toList());
    }

    private static ParadigmReference toDto(ai.speckitty.charter.packs.domain.ParadigmReference reference) {
        return new ParadigmReference(reference.type(), reference.id());
    }
}
