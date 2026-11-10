package ai.speckitty.charter.profiles.infra.api;

import ai.speckitty.charter.api.AgentProfilesApi;
import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.profiles.application.ProfileRead;
import ai.speckitty.charter.profiles.application.ProfileReadService;
import java.util.Objects;
import java.util.OptionalInt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * {@code GET /packs/{packId}/artifacts/agent_profile/{artifactId}}: the pack-scoped read of one agent profile with its
 * governance (FR-002), the profiles slice's inbound adapter. A refusal the read throws is answered by the shared
 * refusal handler; a {@code depth} outside 0 to 1 is refused by the generated interface's bean validation as a 400
 * without a code, before the handler runs.
 *
 * <p>The answer carries the {@code Charter-Source-Fingerprint} the one verified read agreed on and the pack revision as
 * its {@code ETag}, as a directive's read does, because the removal of a profile needs it in {@code If-Match}. The links
 * are built by {@link ProfileDtos}, the way the activations adapter builds its own; this controller names nothing of
 * the packs adapter.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class AgentProfilesController implements AgentProfilesApi {

    private static final String DEPTH = "depth";

    private final ProfileReadService profiles;
    private final ProfileDtos dtos;

    /**
     * A controller over the profile read.
     *
     * @param profiles the read use case
     * @param dtos the wire mapper of the profile and its links
     */
    @Autowired
    public AgentProfilesController(ProfileReadService profiles, ProfileDtos dtos) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.dtos = Objects.requireNonNull(dtos, "dtos");
    }

    @Override
    public ResponseEntity<Artifact> getPackAgentProfile(String packId, String artifactId, Integer depth) {
        ProfileRead read = profiles.read(new PackIdentifier(packId), artifactId, depth);
        return ResponseEntity.ok()
                .header(HeaderNames.SOURCE_FINGERPRINT, read.sourceFingerprint())
                .eTag(EntityTags.quote(read.revision()))
                .body(dtos.artifact(read, depthGiven() ? OptionalInt.of(depth) : OptionalInt.empty()));
    }

    /** Whether the request named {@code depth}, which has a default: the {@code self} link repeats it only then. */
    private static boolean depthGiven() {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .build()
                .getQueryParams()
                .containsKey(DEPTH);
    }
}
