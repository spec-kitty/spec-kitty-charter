package ai.speckitty.charter.profiles.infra.api;

import ai.speckitty.charter.api.EffectiveAgentProfilesApi;
import ai.speckitty.charter.api.model.EffectiveAgentProfileRead;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.profiles.application.EffectiveProfiles;
import ai.speckitty.charter.profiles.application.ProfileReadService;
import java.util.Objects;
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
 * {@code If-None-Match} is a {@code 304} that sends the tag alone, as the activation set's effective reads do.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class EffectiveAgentProfilesController implements EffectiveAgentProfilesApi {

    private static final String DEPTH = "depth";

    private final ProfileReadService profiles;
    private final EffectiveProfileDtos dtos;

    /**
     * A controller over the profile read under an activation.
     *
     * @param profiles the read use case
     * @param dtos the wire mapper of the answer and its links
     */
    @Autowired
    public EffectiveAgentProfilesController(ProfileReadService profiles, EffectiveProfileDtos dtos) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.dtos = Objects.requireNonNull(dtos, "dtos");
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

    /** Whether the request named {@code depth}, which has a default: the {@code self} link repeats it only then. */
    private static boolean depthGiven() {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .build()
                .getQueryParams()
                .containsKey(DEPTH);
    }
}
