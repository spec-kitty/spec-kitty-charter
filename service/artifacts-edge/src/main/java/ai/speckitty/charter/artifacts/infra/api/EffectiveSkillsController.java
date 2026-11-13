package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.EffectiveSkillsApi;
import ai.speckitty.charter.api.model.EffectiveSkillRead;
import ai.speckitty.charter.api.model.SkillList;
import ai.speckitty.charter.artifacts.application.EffectiveSkillReads;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The skill reads under an activation (#107, tag {@code EffectiveSkills}): {@code GET /activations/{activationId}/artifacts/skill}
 * and its item {@code …/skill/{artifactId}}, each the copy the layer merge keeps, the item whatever its state. The
 * literal paths are preferred to the activation set's templated read of an artifact, which carries no body. A refusal
 * the reads throw is answered by the shared refusal handler; the answers are built as the asset reads under an
 * activation build theirs ({@link EffectiveAssetsController#answer}).
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class EffectiveSkillsController implements EffectiveSkillsApi {

    private final EffectiveSkillReads skills;
    private final EffectiveBodies bodies;

    /**
     * A controller over the skill reads under an activation.
     *
     * @param skills the reads
     * @param bodies the wire mapper of the answers and their links
     */
    @Autowired
    public EffectiveSkillsController(EffectiveSkillReads skills, EffectiveBodies bodies) {
        this.skills = Objects.requireNonNull(skills, "skills");
        this.bodies = Objects.requireNonNull(bodies, "bodies");
    }

    @Override
    public ResponseEntity<SkillList> listEffectiveSkills(String activationId, @Nullable String ifNoneMatch) {
        return EffectiveAssetsController.answer(
                skills.list(activationId, ifNoneMatch), listed -> listed.scope().sourceFingerprint(), bodies::skills);
    }

    @Override
    public ResponseEntity<EffectiveSkillRead> getEffectiveSkill(
            String activationId, String artifactId, @Nullable String ifNoneMatch) {
        return EffectiveAssetsController.answer(
                skills.skill(activationId, artifactId, ifNoneMatch),
                read -> read.scope().sourceFingerprint(),
                bodies::skill);
    }
}
