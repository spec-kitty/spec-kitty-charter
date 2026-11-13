package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.SkillsApi;
import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.SkillList;
import ai.speckitty.charter.artifacts.application.SkillReads;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.web.DeferredETag;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The skill reads of a pack (#107, tag {@code Skills}): {@code GET /packs/{packId}/artifacts/skill} and its item
 * {@code …/skill/{artifactId}}. A refusal the reads throw is answered by the shared refusal handler.
 *
 * <p>Both answers carry the pack revision as their {@code ETag} and the fingerprint the one verified read agreed on.
 * Neither takes {@code If-None-Match}; their tag is handed to the web transport's {@link DeferredETag} so that Spring's
 * own conditional check never answers a {@code 304} the contract does not declare.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class SkillsController implements SkillsApi {

    private final SkillReads skills;
    private final SkillBodies bodies;

    /**
     * A controller over the skill reads.
     *
     * @param skills the reads
     * @param bodies the wire mapper of a skill and its links
     */
    @Autowired
    public SkillsController(SkillReads skills, SkillBodies bodies) {
        this.skills = Objects.requireNonNull(skills, "skills");
        this.bodies = Objects.requireNonNull(bodies, "bodies");
    }

    @Override
    public ResponseEntity<SkillList> listPackSkills(String packId) {
        SkillReads.Listed listed = skills.list(new PackIdentifier(packId));
        DeferredETag.offer(EntityTags.quote(listed.revision()));
        return ResponseEntity.ok()
                .header(HeaderNames.SOURCE_FINGERPRINT, listed.sourceFingerprint())
                .body(bodies.list(packId, listed.skills()));
    }

    @Override
    public ResponseEntity<Artifact> getPackSkill(String packId, String artifactId) {
        SkillReads.Read read = skills.skill(new PackIdentifier(packId), artifactId);
        DeferredETag.offer(EntityTags.quote(read.revision()));
        return ResponseEntity.ok()
                .header(HeaderNames.SOURCE_FINGERPRINT, read.sourceFingerprint())
                .body(bodies.artifact(read.skill()));
    }
}
