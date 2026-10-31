package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.StyleguidesApi;
import ai.speckitty.charter.api.model.Styleguide;
import ai.speckitty.charter.api.model.StyleguideAntiPattern;
import ai.speckitty.charter.api.model.StyleguidePattern;
import ai.speckitty.charter.api.model.StyleguideRegistration;
import ai.speckitty.charter.api.model.StyleguideToolEntry;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.application.StyleguideReadService;
import ai.speckitty.charter.packs.application.StyleguideRegistrationService;
import ai.speckitty.charter.packs.domain.ConfigValue;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.StyleguideScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The inbound REST adapter for a pack's styleguides: {@code GET}/{@code POST} on the collection and
 * {@code GET}/{@code PUT} on one styleguide (ADR-0026 functional registration; no {@code DELETE}/{@code PATCH}). A
 * faithful copy of {@link ai.speckitty.charter.packs.infra.api.DirectiveController}, substituting the styleguide
 * types. The read is a thin projection of {@link StyleguideReadService}; the writes delegate to
 * {@link StyleguideRegistrationService}. A refusal the services throw is answered by {@link RefusalHandler}; so is a
 * stale {@code If-Match} precondition, as a plain {@code 412} problem (not a refusal code), per ADR-0020.
 *
 * <p>Bodies are the generated contract DTOs only; a storage DSO is never exposed (C-002). A styleguide mints no
 * relationships, so the response relationships array is always empty and the registration body carries none. The
 * tooling map is carried as ordered {@code {tool, configuration}} entries, and the
 * arbitrary structural-lint-config tree as its JSON text, because a closed response object admits neither an
 * open-keyed map nor a recursive tree.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class StyleguideController implements StyleguidesApi {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final StyleguideReadService reads;
    private final StyleguideRegistrationService registrations;

    /**
     * A controller over the styleguide read and registration use cases.
     *
     * @param reads the read use case
     * @param registrations the registration use case
     */
    public StyleguideController(StyleguideReadService reads, StyleguideRegistrationService registrations) {
        this.reads = reads;
        this.registrations = registrations;
    }

    @Override
    public ResponseEntity<List<Styleguide>> listPackStyleguides(String packId) {
        var verified = reads.styleguidesOf(new PackIdentifier(packId));
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .body(verified.value().stream().map(StyleguideController::toDto).toList());
    }

    @Override
    public ResponseEntity<Styleguide> getPackStyleguide(String packId, String styleguideId) {
        var verified = reads.styleguideWithRevision(new PackIdentifier(packId), new StyleguideIdentifier(styleguideId));
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .eTag(verified.value().revision().token())
                .body(toDto(verified.value().value()));
    }

    @Override
    public ResponseEntity<Styleguide> registerPackStyleguide(String packId, StyleguideRegistration body) {
        PackIdentifier pack = new PackIdentifier(packId);
        String id = requireId(body.getId());
        ai.speckitty.charter.packs.domain.Styleguide styleguide = assemble(
                pack,
                id,
                body.getSchemaVersion(),
                body.getTitle(),
                body.getScope(),
                body.getPrinciples(),
                OptionalLists.orEmpty(body.getPatterns()),
                OptionalLists.orEmpty(body.getAntiPatterns()),
                OptionalLists.orEmpty(body.getTooling()),
                body.getQualityTest(),
                body.getStructuralLintConfig(),
                OptionalLists.orEmpty(body.getAppliesToLanguages()),
                OptionalLists.orEmpty(body.getReferences()));
        StyleguideRegistrationService.Registered result =
                registrations.registerStyleguide(pack, styleguide, false, null);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri()
                        .path("/{id}")
                        .buildAndExpand(id)
                        .toUri())
                .eTag(result.revision().token())
                .body(toDto(styleguide));
    }

    @Override
    public ResponseEntity<Styleguide> putPackStyleguide(
            String packId, String styleguideId, StyleguideRegistration body, @Nullable String ifMatch) {
        PackIdentifier pack = new PackIdentifier(packId);
        ai.speckitty.charter.packs.domain.Styleguide styleguide = assemble(
                pack,
                styleguideId,
                body.getSchemaVersion(),
                body.getTitle(),
                body.getScope(),
                body.getPrinciples(),
                OptionalLists.orEmpty(body.getPatterns()),
                OptionalLists.orEmpty(body.getAntiPatterns()),
                OptionalLists.orEmpty(body.getTooling()),
                body.getQualityTest(),
                body.getStructuralLintConfig(),
                OptionalLists.orEmpty(body.getAppliesToLanguages()),
                OptionalLists.orEmpty(body.getReferences()));
        StyleguideRegistrationService.Registered result =
                registrations.registerStyleguide(pack, styleguide, true, ifMatch);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .eTag(result.revision().token())
                .body(toDto(styleguide));
    }

    // ---- DTO -> domain -------------------------------------------------------------------------------------------

    private static String requireId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED, "A styleguide registration must carry an id on POST.");
        }
        return id;
    }

    private static ai.speckitty.charter.packs.domain.Styleguide assemble(
            PackIdentifier pack,
            String id,
            String schemaVersion,
            String title,
            String scope,
            List<String> principles,
            List<StyleguidePattern> patterns,
            List<StyleguideAntiPattern> antiPatterns,
            List<StyleguideToolEntry> tooling,
            @Nullable String qualityTest,
            @Nullable String structuralLintConfig,
            List<String> appliesToLanguages,
            List<String> references) {
        try {
            var builder = ai.speckitty.charter.packs.domain.Styleguide.builder()
                    .id(new StyleguideIdentifier(id))
                    .packId(pack)
                    .schemaVersion(schemaVersion)
                    .title(title)
                    .scope(StyleguideScope.fromToken(scope)
                            .orElseThrow(
                                    () -> new CharterRefusal(RefusalCode.VALIDATION_FAILED, "unknown scope: " + scope)))
                    .qualityTest(qualityTest);
            principles.forEach(builder::principle);
            if (!patterns.isEmpty()) {
                builder.patterns(patterns.stream()
                        .map(p -> new ai.speckitty.charter.packs.domain.StyleguidePattern(
                                p.getName(),
                                p.getDescription(),
                                Optional.ofNullable(p.getBadExample()),
                                Optional.ofNullable(p.getGoodExample())))
                        .toList());
            }
            if (!antiPatterns.isEmpty()) {
                builder.antiPatterns(antiPatterns.stream()
                        .map(a -> new ai.speckitty.charter.packs.domain.StyleguideAntiPattern(
                                a.getName(), a.getDescription(), a.getBadExample(), a.getGoodExample()))
                        .toList());
            }
            if (!tooling.isEmpty()) {
                Map<String, String> map = new LinkedHashMap<>();
                tooling.forEach(entry -> map.put(entry.getTool(), entry.getConfiguration()));
                builder.tooling(map);
            }
            if (structuralLintConfig != null) {
                builder.structuralLintConfig(ConfigValue.of(parseConfig(structuralLintConfig)));
            }
            appliesToLanguages.forEach(builder::appliesToLanguage);
            references.forEach(builder::reference);
            return builder.build();
        } catch (ConstraintBreachException | IllegalArgumentException invalid) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(invalid.getMessage()), invalid);
        }
    }

    private static @Nullable Object parseConfig(String json) {
        try {
            return JSON.readValue(json, Object.class);
        } catch (JacksonException malformed) {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED, "structuralLintConfig is not valid JSON: " + malformed.getMessage());
        }
    }

    // ---- domain -> DTO -------------------------------------------------------------------------------------------

    private static Styleguide toDto(ai.speckitty.charter.packs.domain.Styleguide styleguide) {
        return new Styleguide(
                styleguide.id().value(),
                styleguide.schemaVersion().orElseThrow(),
                styleguide.title(),
                styleguide.scope().token(),
                styleguide.principles(),
                styleguide.patterns().stream().map(StyleguideController::toDto).toList(),
                styleguide.antiPatterns().stream()
                        .map(StyleguideController::toDto)
                        .toList(),
                toToolingDto(styleguide.tooling()),
                styleguide.qualityTest().orElse(null),
                styleguide
                        .structuralLintConfig()
                        .map(StyleguideController::toConfigDto)
                        .orElse(null),
                styleguide.appliesToLanguages(),
                styleguide.references(),
                styleguide.relationships().stream()
                        .map(RelationshipWire::toWire)
                        .toList());
    }

    private static StyleguidePattern toDto(ai.speckitty.charter.packs.domain.StyleguidePattern pattern) {
        return new StyleguidePattern(
                pattern.name(),
                pattern.description(),
                pattern.badExample().orElse(null),
                pattern.goodExample().orElse(null));
    }

    private static StyleguideAntiPattern toDto(ai.speckitty.charter.packs.domain.StyleguideAntiPattern antiPattern) {
        return new StyleguideAntiPattern(
                antiPattern.name(), antiPattern.description(), antiPattern.badExample(), antiPattern.goodExample());
    }

    private static List<StyleguideToolEntry> toToolingDto(Optional<Map<String, String>> tooling) {
        return tooling.map(map -> map.entrySet().stream()
                        .map(entry -> new StyleguideToolEntry(entry.getKey(), entry.getValue()))
                        .toList())
                .orElse(List.of());
    }

    private static String toConfigDto(ConfigValue config) {
        return JSON.writeValueAsString(config.toParsed());
    }
}
