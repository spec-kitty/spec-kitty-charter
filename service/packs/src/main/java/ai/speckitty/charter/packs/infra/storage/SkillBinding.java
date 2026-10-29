package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.Skill;
import ai.speckitty.charter.packs.domain.SkillExpansion;
import ai.speckitty.charter.packs.domain.SkillForm;
import ai.speckitty.charter.packs.domain.SkillIdentifier;
import ai.speckitty.charter.packs.domain.SkillInvocation;
import ai.speckitty.charter.packs.domain.SkillParameter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The {@link KindBinding} of the pack skill: {@link SkillDso} to {@link Skill} and back. Its files are
 * {@code *.skill.yaml}, validated by the {@code skill} schema. A prompt skill's body is its one companion file
 * (ADR-0039): the file its {@code body_path} names, relative to the skill's file, read and written with it.
 */
enum SkillBinding implements KindBinding<SkillDso, Skill> {
    /** The binding. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.SKILL;
    }

    @Override
    public Class<Skill> entityType() {
        return Skill.class;
    }

    @Override
    public Class<SkillDso> dsoType() {
        return SkillDso.class;
    }

    @Override
    public String idOf(SkillDso dso) {
        return dso.id();
    }

    @Override
    public boolean hasCompanions() {
        return true;
    }

    /** The prompt body, when the skill names one: a text companion, read and written byte for byte. */
    @Override
    public List<Companion> companions(SkillDso dso) {
        return dso.bodyPath()
                .map(path -> List.of(new Companion(
                        path, dso.body().map(CompanionContent.Text::new), ContentForm.TEXT, Optional.empty())))
                .orElse(List.of());
    }

    @Override
    public SkillDso withCompanions(SkillDso dso, Map<String, CompanionContent> contents) {
        return dso.withBody(dso.bodyPath().map(contents::get).map(Companions::text));
    }

    @Override
    public Skill toEntity(PackIdentifier pack, SkillDso dso) {
        return Skill.builder()
                .id(new SkillIdentifier(dso.id()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .title(dso.title())
                .description(dso.description())
                .triggers(dso.triggers())
                .form(SkillForm.fromToken(dso.form()))
                .bodyPath(dso.bodyPath())
                .body(dso.body())
                .expandsTo(dso.expandsTo().map(e -> new SkillExpansion(e.target(), e.args())))
                .parameters(dso.parameters().stream()
                        .map(p -> new SkillParameter(p.name(), p.required(), p.description(), p.defaultValue()))
                        .toList())
                .invocation(dso.invocation()
                        .map(i -> new SkillInvocation(i.userInvocable(), i.modelInvocable(), i.sideEffects())))
                .tools(dso.tools())
                .version(dso.version())
                .maintainers(dso.maintainers())
                .overrides(dso.overrides())
                .enhances(dso.enhances())
                .build();
    }

    @Override
    public SkillDso toDso(Skill skill) {
        return new SkillDso(
                skill.schemaVersion().orElseThrow(),
                skill.id().value(),
                skill.title(),
                skill.description().orElseThrow(),
                skill.triggers(),
                skill.form().token(),
                skill.bodyPath(),
                skill.expandsTo().map(e -> new SkillDso.Expansion(e.target(), e.args())),
                skill.parameters().stream()
                        .map(p -> new SkillDso.Parameter(p.name(), p.required(), p.description(), p.defaultValue()))
                        .toList(),
                skill.invocation()
                        .map(i -> new SkillDso.Invocation(i.userInvocable(), i.modelInvocable(), i.sideEffects())),
                skill.tools(),
                skill.version(),
                skill.maintainers(),
                skill.overrides(),
                skill.enhances(),
                skill.body());
    }
}
