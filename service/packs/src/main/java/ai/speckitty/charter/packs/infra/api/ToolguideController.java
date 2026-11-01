package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.ToolguidesApi;
import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.ToolguideRegistration;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.application.GuideFormat;
import ai.speckitty.charter.packs.application.ToolguideRegistrationService;
import ai.speckitty.charter.packs.application.ToolguideRegistrationService.GuideSource;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import java.net.URI;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The inbound REST adapter for registering a pack's toolguides under the artifact tree: {@code POST
 * /packs/{packId}/artifacts/toolguide} and {@code PUT /packs/{packId}/artifacts/toolguide/{artifactId}} (ADR-0026
 * functional registration; no {@code DELETE}/{@code PATCH}). It copies {@link DirectiveController}: a toolguide is read
 * through the generic {@link ArtifactController}, each write answers the written toolguide in the same
 * {@code Artifact} envelope, and the writes delegate to {@link ToolguideRegistrationService}. A refusal the services throw is answered by {@link RefusalHandler}; so is a
 * stale {@code If-Match} precondition, as a plain {@code 412} problem (not a refusal code), per ADR-0020.
 *
 * <p>A toolguide mints no relationships, so neither the request body nor the response carries a relationships array.
 * Bodies are the generated contract DTOs only; a storage DSO is never exposed (C-002).
 *
 * <p>The guide travels as content, never as a path (FR-010): a registration carries exactly one of {@code guide} (its
 * Markdown) or {@code guideUri} (fetched by the service), and the response carries the stored guide's Markdown with its
 * H1 written from the title (FR-011). The service assumes no filesystem shared with its client. An inline guide is
 * capped at {@code charter.guide-fetch.max-size} (1 MiB by default), the cap a fetched guide is held to, so the two
 * sources of a guide obey one limit.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class ToolguideController implements ToolguidesApi {

    /** The default cap on a guide, in bytes of UTF-8 (1 MiB, NFR-005): {@code charter.guide-fetch.max-size}. */
    static final int MAX_GUIDE_BYTES = 1024 * 1024;

    private static final int MIB = 1024 * 1024;

    /** The most items a registration's {@code commands}, {@code appliesToLanguages} or {@code references} holds. */
    static final int MAX_LIST_ITEMS = 256;

    private final ToolguideRegistrationService registrations;
    private final Hypermedia links;
    private final GuideFormat guides;
    private final long maxGuideBytes;

    /**
     * A controller over the toolguide registration use case, with the default guide cap.
     *
     * @param registrations the registration use case
     * @param guides the guide's Markdown form, in which a guide is answered
     * @param links the link builder of the answers
     */
    ToolguideController(ToolguideRegistrationService registrations, GuideFormat guides, Hypermedia links) {
        this(registrations, guides, links, DataSize.ofBytes(MAX_GUIDE_BYTES));
    }

    /**
     * A controller over the toolguide registration use case.
     *
     * @param registrations the registration use case
     * @param guides the guide's Markdown form, in which a guide is answered
     * @param links the link builder of the answers
     * @param maxGuideSize the largest inline guide, {@code charter.guide-fetch.max-size}: the cap the fetcher holds a
     *     fetched guide to
     */
    @Autowired
    ToolguideController(
            ToolguideRegistrationService registrations,
            GuideFormat guides,
            Hypermedia links,
            @Value("${charter.guide-fetch.max-size:1MB}") DataSize maxGuideSize) {
        this.registrations = registrations;
        this.links = links;
        this.guides = guides;
        this.maxGuideBytes = maxGuideSize.toBytes();
        if (maxGuideBytes <= 0 || maxGuideBytes > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("the guide size cap must be positive and below 2 GiB");
        }
    }

    @Override
    public ResponseEntity<Artifact> registerPackToolguide(String packId, ToolguideRegistration body) {
        PackIdentifier pack = new PackIdentifier(packId);
        String id = requireId(body.getId());
        ToolguideRegistrationService.Registered result = register(pack, id, body, false, null);
        Artifact written = written(pack, id, result);
        return ResponseEntity.created(ArtifactDtos.location(written))
                .eTag(result.revision().token())
                .body(written);
    }

    @Override
    public ResponseEntity<Artifact> putPackToolguide(
            String packId, String artifactId, ToolguideRegistration body, @Nullable String ifMatch) {
        PackIdentifier pack = new PackIdentifier(packId);
        ToolguideRegistrationService.Registered result = register(pack, artifactId, body, true, ifMatch);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .eTag(result.revision().token())
                .body(written(pack, artifactId, result));
    }

    /** The written toolguide in the envelope the item read answers, with its links. */
    private Artifact written(PackIdentifier pack, String id, ToolguideRegistrationService.Registered result) {
        return ArtifactDtos.envelope(ArtifactKind.TOOLGUIDE, pack, id, links)
                .toolguide(ArtifactDtos.toolguide(result.toolguide(), guides));
    }

    // ---- DTO -> domain -------------------------------------------------------------------------------------------

    private ToolguideRegistrationService.Registered register(
            PackIdentifier pack, String id, ToolguideRegistration body, boolean replace, @Nullable String ifMatch) {
        GuideSource guide = guideSource(body.getGuide(), body.getGuideUri());
        return registrations.registerToolguide(
                pack,
                identifier(id),
                body.getTitle(),
                guide,
                resolved -> assemble(pack, id, body, resolved),
                replace,
                ifMatch);
    }

    private static ToolguideIdentifier identifier(String id) {
        try {
            return new ToolguideIdentifier(id);
        } catch (ConstraintBreachException invalid) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(invalid.getMessage()), invalid);
        }
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
     * generated DTO enforces of the contract's {@code oneOf} (FR-010). Both, neither or an empty value is refused (an
     * explicit {@code null} is refused before the body is read, by {@link ToolguideRegistrationBodyAdvice}), and so is an
     * inline guide over the cap of UTF-8 or one that is not Unicode text (an unpaired surrogate), before it is
     * parsed (NFR-005). In the deployed service an empty {@code guide} never reaches this check: it breaks the
     * contract's {@code minLength} and bean validation refuses it 400 before the controller (ADR-0041 decision 2); the
     * check stays as defence in depth for a wiring without bean validation. An empty {@code guideUri} does reach it:
     * the contract declares no {@code minLength} on a field the generator types {@link URI}.
     */
    private GuideSource guideSource(@Nullable String guide, @Nullable URI guideUri) {
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
            if (guide.length() > maxGuideBytes || utf8Length(guide) > maxGuideBytes) {
                throw new CharterRefusal(
                        RefusalCode.VALIDATION_FAILED,
                        "The guide is larger than " + maxGuideBytes + " bytes of UTF-8" + mebibytes() + ".");
            }
            return new GuideSource.Content(guide);
        }
        URI uri = Objects.requireNonNull(guideUri, "guideUri");
        if (uri.toString().isEmpty()) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, "The guideUri is empty.");
        }
        return new GuideSource.Location(uri);
    }

    /** The cap in MiB, in parentheses, when it is a whole number of them; nothing otherwise. */
    private String mebibytes() {
        return maxGuideBytes % MIB == 0 ? " (" + maxGuideBytes / MIB + " MiB)" : "";
    }

    /**
     * The length of the guide in UTF-8, encoded strictly: a lone surrogate (which a JSON escape can spell) has no
     * UTF-8 form and is refused, rather than stored as {@code ?}, which a fetched guide, decoded strictly, never is.
     */
    private static int utf8Length(String guide) {
        try {
            return StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(guide))
                    .remaining();
        } catch (CharacterCodingException unpaired) {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED,
                    "The guide is not Unicode text: it holds an unpaired surrogate, which has no UTF-8 form.");
        }
    }

    /**
     * The domain toolguide a registration describes, around the guide the service resolved. The guide must also be
     * writable under the title (its H1), so a title that cannot be an H1 is refused here, not when it is stored: one
     * that is blank or more than one line, or one the Markdown reader would not read back as itself (a title ending in
     * a closing {@code #} sequence, such as {@code "Search for #"}).
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
            bounded("commands", OptionalLists.orEmpty(body.getCommands())).forEach(builder::command);
            bounded("appliesToLanguages", OptionalLists.orEmpty(body.getAppliesToLanguages()))
                    .forEach(builder::appliesToLanguage);
            bounded("references", OptionalLists.orEmpty(body.getReferences())).forEach(builder::reference);
            ai.speckitty.charter.packs.domain.Toolguide toolguide = builder.build();
            guides.write(toolguide.title(), guide);
            return toolguide;
        } catch (ConstraintBreachException | IllegalArgumentException invalid) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(invalid.getMessage()), invalid);
        }
    }

    /**
     * The list, or a refusal when it holds more than {@link #MAX_LIST_ITEMS} (the contract's {@code maxItems}). In the
     * deployed service bean validation refuses such a list 400 before the controller (ADR-0041 decision 2); this stays
     * as defence in depth for a wiring without bean validation.
     */
    private static List<String> bounded(String field, List<String> items) {
        if (items.size() > MAX_LIST_ITEMS) {
            throw new IllegalArgumentException("The " + field + " list holds " + items.size() + " items; at most "
                    + MAX_LIST_ITEMS + " are accepted.");
        }
        return items;
    }
}
