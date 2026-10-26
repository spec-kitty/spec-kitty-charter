package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.ToolguidesApi;
import ai.speckitty.charter.api.model.Toolguide;
import ai.speckitty.charter.api.model.ToolguideRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.application.ToolguideReadService;
import ai.speckitty.charter.packs.application.ToolguideRegistrationService;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The inbound REST adapter for a pack's toolguides: {@code GET}/{@code POST} on the collection and
 * {@code GET}/{@code PUT} on one toolguide (ADR-0026 functional registration; no {@code DELETE}/{@code PATCH}). It
 * copies {@link DirectiveController}: the read is a thin projection of {@link ToolguideReadService}; the writes delegate
 * to {@link ToolguideRegistrationService}. A refusal the services throw is answered by {@link RefusalHandler}; so is a
 * stale {@code If-Match} precondition, as a plain {@code 412} problem (not a refusal code), per ADR-0020.
 *
 * <p>A toolguide mints no relationships, so neither the request body nor the response carries a relationships array.
 * Bodies are the generated contract DTOs only; a storage DSO is never exposed (C-002).
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class ToolguideController implements ToolguidesApi {

    private final ToolguideReadService reads;
    private final ToolguideRegistrationService registrations;

    /**
     * A controller over the toolguide read and registration use cases.
     *
     * @param reads the read use case
     * @param registrations the registration use case
     */
    public ToolguideController(ToolguideReadService reads, ToolguideRegistrationService registrations) {
        this.reads = reads;
        this.registrations = registrations;
    }

    @Override
    public ResponseEntity<List<Toolguide>> listPackToolguides(String packId) {
        var verified = reads.toolguidesOf(new PackIdentifier(packId));
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .body(verified.value().stream().map(ToolguideController::toDto).toList());
    }

    @Override
    public ResponseEntity<Toolguide> getPackToolguide(String packId, String toolguideId) {
        var verified = reads.toolguideWithRevision(new PackIdentifier(packId), new ToolguideIdentifier(toolguideId));
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .eTag(verified.value().revision().token())
                .body(toDto(verified.value().value()));
    }

    @Override
    public ResponseEntity<Toolguide> registerPackToolguide(String packId, ToolguideRegistration body) {
        PackIdentifier pack = new PackIdentifier(packId);
        String id = requireId(body.getId());
        ai.speckitty.charter.packs.domain.Toolguide toolguide = assemble(
                pack,
                id,
                body.getSchemaVersion(),
                body.getTool(),
                body.getTitle(),
                body.getGuidePath(),
                body.getSummary(),
                body.getCommands(),
                body.getAppliesToLanguages(),
                body.getLastUpdated(),
                body.getReferences());
        ToolguideRegistrationService.Registered result = registrations.registerToolguide(pack, toolguide, false, null);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri()
                        .path("/{id}")
                        .buildAndExpand(id)
                        .toUri())
                .eTag(result.revision().token())
                .body(toDto(toolguide));
    }

    @Override
    public ResponseEntity<Toolguide> putPackToolguide(
            String packId, String toolguideId, ToolguideRegistration body, @Nullable String ifMatch) {
        PackIdentifier pack = new PackIdentifier(packId);
        ai.speckitty.charter.packs.domain.Toolguide toolguide = assemble(
                pack,
                toolguideId,
                body.getSchemaVersion(),
                body.getTool(),
                body.getTitle(),
                body.getGuidePath(),
                body.getSummary(),
                body.getCommands(),
                body.getAppliesToLanguages(),
                body.getLastUpdated(),
                body.getReferences());
        ToolguideRegistrationService.Registered result =
                registrations.registerToolguide(pack, toolguide, true, ifMatch);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .eTag(result.revision().token())
                .body(toDto(toolguide));
    }

    // ---- DTO -> domain -------------------------------------------------------------------------------------------

    private static String requireId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED, "A toolguide registration must carry an id on POST.");
        }
        return id;
    }

    private static ai.speckitty.charter.packs.domain.Toolguide assemble(
            PackIdentifier pack,
            String id,
            String schemaVersion,
            String tool,
            String title,
            String guidePath,
            String summary,
            List<String> commands,
            List<String> appliesToLanguages,
            @Nullable String lastUpdated,
            List<String> references) {
        try {
            var builder = ai.speckitty.charter.packs.domain.Toolguide.builder()
                    .id(new ToolguideIdentifier(id))
                    .packId(pack)
                    .schemaVersion(schemaVersion)
                    .tool(tool)
                    .title(title)
                    .guidePath(guidePath)
                    .summary(summary)
                    .lastUpdated(lastUpdated);
            commands.forEach(builder::command);
            appliesToLanguages.forEach(builder::appliesToLanguage);
            references.forEach(builder::reference);
            return builder.build();
        } catch (ConstraintBreachException | IllegalArgumentException invalid) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(invalid.getMessage()), invalid);
        }
    }

    // ---- domain -> DTO -------------------------------------------------------------------------------------------

    private static Toolguide toDto(ai.speckitty.charter.packs.domain.Toolguide toolguide) {
        return new Toolguide(
                toolguide.id().value(),
                toolguide.schemaVersion(),
                toolguide.tool(),
                toolguide.title(),
                toolguide.guidePath(),
                toolguide.summary(),
                toolguide.commands(),
                toolguide.appliesToLanguages(),
                toolguide.lastUpdated().orElse(null),
                toolguide.references());
    }
}
