package ai.speckitty.charter.profiles.infra.api;

import ai.speckitty.charter.api.EffectiveAgentProfilesApi;
import ai.speckitty.charter.api.model.EffectiveAgentProfileRead;
import ai.speckitty.charter.api.model.ProfileSelectionRead;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.profiles.application.EffectiveProfiles;
import ai.speckitty.charter.profiles.application.ProfileReadService;
import ai.speckitty.charter.profiles.application.ProfileSelectionService;
import ai.speckitty.charter.profiles.domain.selection.SelectionContext;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * {@code GET /activations/{activationId}/artifacts/agent_profile/{artifactId}}: the read of one agent profile under an
 * activation (FR-007), the profiles slice's second inbound adapter. It is a controller of its own, of its own tag
 * ({@code EffectiveAgentProfiles}), beside {@link AgentProfilesController}: one tag per controller leaves no shared
 * owned file and no duplicate Spring mapping. The literal path is preferred to the activation set's templated read of an
 * artifact, which carries no body.
 *
 * <p>A refusal the read throws is answered by the shared refusal handler; a {@code depth} outside 0 to 1 is refused by
 * the generated interface's bean validation as a 400 without a code, before the handler runs, and answers the body the
 * activation routes answer ({@code ProfileProblemParityTest}). The answer carries the {@code Charter-Source-Fingerprint}
 * the one verified read agreed on and the tag of the representation as its {@code ETag}; a matching
 * {@code If-None-Match} is a {@code 304} that sends the tag alone, as the activation set's effective reads do. The
 * selection of the profile that fills a role ({@code selectAgentProfile}) is of the same tag, so it is served here too,
 * over its own use case and wire mapper.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class EffectiveAgentProfilesController implements EffectiveAgentProfilesApi {

    private static final String DEPTH = "depth";

    private final ProfileReadService profiles;
    private final EffectiveProfileDtos dtos;
    private final ProfileSelectionService selection;
    private final ProfileSelectionDtos selectionDtos;

    /**
     * A controller over the profile read and the profile selection under an activation.
     *
     * @param profiles the read use case
     * @param dtos the wire mapper of the read's answer and its links
     * @param selection the selection use case
     * @param selectionDtos the wire mapper of the selection's answer and its links
     */
    @Autowired
    public EffectiveAgentProfilesController(
            ProfileReadService profiles,
            EffectiveProfileDtos dtos,
            ProfileSelectionService selection,
            ProfileSelectionDtos selectionDtos) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.dtos = Objects.requireNonNull(dtos, "dtos");
        this.selection = Objects.requireNonNull(selection, "selection");
        this.selectionDtos = Objects.requireNonNull(selectionDtos, "selectionDtos");
    }

    @Override
    public ResponseEntity<EffectiveAgentProfileRead> getEffectiveAgentProfile(
            String activationId, String artifactId, @Nullable String ifNoneMatch, Integer depth) {
        return switch (profiles.underActivation(activationId, artifactId, depth, ifNoneMatch)) {
            case EffectiveProfiles.Outcome.NotModified notModified ->
                ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(EntityTags.quote(notModified.etag()))
                        .build();
            case EffectiveProfiles.Outcome.Found found ->
                ResponseEntity.ok()
                        .eTag(EntityTags.quote(found.etag()))
                        .header(HeaderNames.SOURCE_FINGERPRINT, found.read().sourceFingerprint())
                        .body(dtos.read(found.read(), depthGiven() ? OptionalInt.of(depth) : OptionalInt.empty()));
        };
    }

    /**
     * {@code selectAgentProfile}: the profile that fills a role under an activation (ruling D1 of slice G). The
     * operation shares the tag, and so the generated interface, of the profile read: one controller per tag leaves no
     * duplicate Spring mapping. A role outside its pattern ({@code generic} included), and a language or framework
     * outside its own, is refused by the generated interface's bean validation as a 400 without a code; a refusal of the
     * selection is answered by the shared refusal handler. The answer carries the verified fingerprint and the tag of the
     * representation, and a matching {@code If-None-Match} is a {@code 304} that sends the tag alone.
     */
    @Override
    public ResponseEntity<ProfileSelectionRead> selectAgentProfile(
            String activationId,
            String role,
            @Nullable String ifNoneMatch,
            @Nullable String language,
            @Nullable String framework) {
        SelectionContext context = new SelectionContext(Optional.ofNullable(language), Optional.ofNullable(framework));
        return switch (selection.select(activationId, role, context, ifNoneMatch)) {
            case ProfileSelectionService.Outcome.NotModified notModified ->
                ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(EntityTags.quote(notModified.etag()))
                        .build();
            case ProfileSelectionService.Outcome.Found found ->
                ResponseEntity.ok()
                        .eTag(EntityTags.quote(found.etag()))
                        .header(HeaderNames.SOURCE_FINGERPRINT, found.sourceFingerprint())
                        .body(selectionDtos.read(found.selection()));
        };
    }

    /** Whether the request named {@code depth}, which has a default: the {@code self} link repeats it only then. */
    private static boolean depthGiven() {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .build()
                .getQueryParams()
                .containsKey(DEPTH);
    }
}
