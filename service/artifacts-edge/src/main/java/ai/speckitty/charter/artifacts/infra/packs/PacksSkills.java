package ai.speckitty.charter.artifacts.infra.packs;

import ai.speckitty.charter.artifacts.application.SkillReads;
import ai.speckitty.charter.artifacts.application.SkillView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import ai.speckitty.charter.packs.domain.Skill;
import ai.speckitty.charter.packs.domain.SkillExpansion;
import ai.speckitty.charter.packs.domain.SkillInvocation;
import ai.speckitty.charter.packs.domain.SkillParameter;
import java.util.Comparator;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The {@link SkillReads} over the packs slice: the one class of the artifacts edge that asks it for skills, and only its
 * application ring ({@link ForeignArtifactRead}) and domain. Each read is one verified read of the packs slice; this
 * adapter narrows the entities to the packs domain's {@link Skill} and maps them to the edge's own {@link SkillView},
 * so nothing above it names a packs type. The body is the skill's {@code text} companion as the pack holds it,
 * unchanged.
 */
@Component
public final class PacksSkills implements SkillReads {

    private final ForeignArtifactRead packs;

    /**
     * Reads over the packs slice's read of the artifacts another slice renders.
     *
     * @param packs the verified reads of an artifact and of a kind's artifacts
     */
    @Autowired
    public PacksSkills(ForeignArtifactRead packs) {
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    @Override
    public Listed list(PackIdentifier pack) {
        ConsistentRead.Verified<ForeignArtifactRead.Held> verified = packs.held(pack, ArtifactKind.SKILL);
        return new Listed(
                verified.value().artifacts().stream()
                        .map(Skill.class::cast)
                        .map(PacksSkills::viewOf)
                        .sorted(Comparator.comparing(SkillView::id))
                        .toList(),
                verified.value().revision().token(),
                verified.fingerprint());
    }

    @Override
    public Read skill(PackIdentifier pack, String skillId) {
        ConsistentRead.Verified<ForeignArtifactRead.Foreign> verified =
                packs.read(pack, ArtifactKind.SKILL, skillId, 0);
        return new Read(
                viewOf(Skill.class.cast(verified.value().artifact())),
                verified.value().revision().token(),
                verified.fingerprint());
    }

    @Override
    public Listed list(PackIdentifier pack, String expectedFingerprint) {
        Listed listed = list(pack);
        Fingerprints.require(listed.sourceFingerprint(), expectedFingerprint);
        return listed;
    }

    @Override
    public SkillView view(CharterArtifact skill, String read) {
        if (!(skill instanceof Skill held)) {
            throw new IllegalStateException(read + " holds " + skill.kind().token() + " " + skill.localId()
                    + ", which is no skill of the packs slice.");
        }
        return viewOf(held);
    }

    /**
     * The edge's view of a packs skill: every field the file declares except the body path, {@code overrides} and
     * {@code enhances}.
     *
     * @param skill the packs slice's skill
     * @return the view
     */
    static SkillView viewOf(Skill skill) {
        return new SkillView(
                skill.localId(),
                skill.packId(),
                skill.schemaVersion().orElseThrow(),
                skill.title(),
                skill.description().orElseThrow(),
                skill.form().token(),
                skill.triggers(),
                skill.parameters().stream().map(PacksSkills::parameterOf).toList(),
                skill.invocation().map(PacksSkills::invocationOf),
                skill.expandsTo().map(PacksSkills::expansionOf),
                skill.tools(),
                skill.version(),
                skill.maintainers(),
                skill.body());
    }

    private static SkillView.Parameter parameterOf(SkillParameter parameter) {
        return new SkillView.Parameter(
                parameter.name(), parameter.required(), parameter.description(), parameter.defaultValue());
    }

    private static SkillView.Invocation invocationOf(SkillInvocation invocation) {
        return new SkillView.Invocation(
                invocation.userInvocable(), invocation.modelInvocable(), invocation.sideEffects());
    }

    private static SkillView.Expansion expansionOf(SkillExpansion expansion) {
        return new SkillView.Expansion(expansion.target(), expansion.args());
    }
}
