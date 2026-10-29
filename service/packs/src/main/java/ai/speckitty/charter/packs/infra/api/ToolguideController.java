package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.ToolguidesApi;
import ai.speckitty.charter.api.model.Toolguide;
import ai.speckitty.charter.api.model.ToolguideRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.application.GuideFormat;
import ai.speckitty.charter.packs.application.ToolguideReadService;
import ai.speckitty.charter.packs.application.ToolguideRegistrationService;
import ai.speckitty.charter.packs.application.ToolguideRegistrationService.GuideSource;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
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
 *
 * <p>The guide travels as content, never as a path (FR-010): a registration carries exactly one of {@code guide} (its
 * Markdown) or {@code guideUri} (fetched by the service), and the response carries the stored guide's Markdown with its
 * H1 written from the title (FR-011). The service assumes no filesystem shared with its client.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class ToolguideController implements ToolguidesApi {

    /** The largest inline guide, in bytes of UTF-8 (1 MiB, NFR-005); the fetcher caps a fetched guide alike. */
    static final int MAX_GUIDE_BYTES = 1024 * 1024;

    private final ToolguideReadService reads;
    private final ToolguideRegistrationService registrations;
    private final GuideFormat guides;

    /**
     * A controller over the toolguide read and registration use cases.
     *
     * @param reads the read use case
     * @param registrations the registration use case
     * @param guides the guide's Markdown form, in which a guide is answered
     */
    public ToolguideController(
            ToolguideReadService reads, ToolguideRegistrationService registrations, GuideFormat guides) {
        this.reads = reads;
        this.registrations = registrations;
        this.guides = guides;
    }

    @Override
    public ResponseEntity<List<Toolguide>> listPackToolguides(String packId) {
        var verified = reads.toolguidesOf(new PackIdentifier(packId));
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .body(verified.value().stream().map(this::toDto).toList());
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
        ToolguideRegistrationService.Registered result = register(pack, id, body, false, null);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri()
                        .path("/{id}")
                        .buildAndExpand(id)
                        .toUri())
                .eTag(result.revision().token())
                .body(toDto(result.toolguide()));
    }

    @Override
    public ResponseEntity<Toolguide> putPackToolguide(
            String packId, String toolguideId, ToolguideRegistration body, @Nullable String ifMatch) {
        ToolguideRegistrationService.Registered result =
                register(new PackIdentifier(packId), toolguideId, body, true, ifMatch);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .eTag(result.revision().token())
                .body(toDto(result.toolguide()));
    }

    // ---- DTO -> domain -------------------------------------------------------------------------------------------

    private ToolguideRegistrationService.Registered register(
            PackIdentifier pack, String id, ToolguideRegistration body, boolean replace, @Nullable String ifMatch) {
        GuideSource guide = guideSource(body.getGuide(), body.getGuideUri());
        return registrations.registerToolguide(
                pack, body.getTitle(), guide, resolved -> assemble(pack, id, body, resolved), replace, ifMatch);
    }

    private static String requireId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED, "A toolguide registration must carry an id on POST.");
        }
        return id;
    }

    /**
     * Where the registration's guide comes from: exactly one of {@code guide} or {@code guideUri}, whatever the
     * generated DTO enforces of the contract's {@code oneOf} (FR-010). Both, neither, a null or an empty value is
     * refused, and so is an inline guide over {@link #MAX_GUIDE_BYTES} of UTF-8, before it is parsed (NFR-005).
     */
    private static GuideSource guideSource(@Nullable String guide, @Nullable URI guideUri) {
        if ((guide == null) == (guideUri == null)) {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED,
                    "A toolguide registration carries exactly one of guide (the Markdown content) or guideUri; this one"
                            + " carries " + (guide == null ? "neither" : "both") + ".");
        }
        if (guide != null) {
            if (guide.isEmpty()) {
                throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, "The guide is empty.");
            }
            // A char is at least one UTF-8 byte: the length bounds the encoding before it is computed.
            if (guide.length() > MAX_GUIDE_BYTES || guide.getBytes(StandardCharsets.UTF_8).length > MAX_GUIDE_BYTES) {
                throw new CharterRefusal(
                        RefusalCode.VALIDATION_FAILED,
                        "The guide is larger than " + MAX_GUIDE_BYTES + " bytes of UTF-8 (1 MiB).");
            }
            return new GuideSource.Content(guide);
        }
        URI uri = Objects.requireNonNull(guideUri, "guideUri");
        if (uri.toString().isEmpty()) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, "The guideUri is empty.");
        }
        return new GuideSource.Location(uri);
    }

    /**
     * The domain toolguide a registration describes, around the guide the service resolved. The guide must also be
     * writable under the title (its H1), so a title that cannot be an H1 is refused here, not when it is stored.
     */
    private ai.speckitty.charter.packs.domain.Toolguide assemble(
            PackIdentifier pack, String id, ToolguideRegistration body, Guide guide) {
        try {
            var builder = ai.speckitty.charter.packs.domain.Toolguide.builder()
                    .id(new ToolguideIdentifier(id))
                    .packId(pack)
                    .schemaVersion(body.getSchemaVersion())
                    .tool(body.getTool())
                    .title(body.getTitle())
                    .summary(body.getSummary())
                    .guide(guide)
                    .lastUpdated(body.getLastUpdated());
            body.getCommands().forEach(builder::command);
            body.getAppliesToLanguages().forEach(builder::appliesToLanguage);
            body.getReferences().forEach(builder::reference);
            ai.speckitty.charter.packs.domain.Toolguide toolguide = builder.build();
            guides.write(toolguide.title(), guide);
            return toolguide;
        } catch (ConstraintBreachException | IllegalArgumentException invalid) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(invalid.getMessage()), invalid);
        }
    }

    // ---- domain -> DTO -------------------------------------------------------------------------------------------

    private Toolguide toDto(ai.speckitty.charter.packs.domain.Toolguide toolguide) {
        return new Toolguide(
                toolguide.id().value(),
                toolguide.schemaVersion().orElseThrow(),
                toolguide.tool(),
                toolguide.title(),
                guides.write(toolguide.title(), toolguide.guide()),
                toolguide.summary(),
                toolguide.commands(),
                toolguide.appliesToLanguages(),
                toolguide.lastUpdated().orElse(null),
                toolguide.references());
    }
}
