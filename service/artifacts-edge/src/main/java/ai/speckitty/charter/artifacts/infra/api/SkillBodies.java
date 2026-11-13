package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.Skill;
import ai.speckitty.charter.api.model.SkillExpansion;
import ai.speckitty.charter.api.model.SkillInvocation;
import ai.speckitty.charter.api.model.SkillList;
import ai.speckitty.charter.api.model.SkillParameter;
import ai.speckitty.charter.api.model.SkillSummary;
import ai.speckitty.charter.artifacts.application.SkillView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * The wire form of a skill (#107, research R8): the one mapper of a skill to the {@code Skill} member, its list summary
 * and the links of the pack-scoped reads. The activation-scoped skill read renders its member with
 * {@link #skill(SkillView)} too, so a skill and its body are mapped once.
 *
 * <p>The skill's own read answers the {@code Artifact} envelope with {@code self} and {@code collection} (the pack's
 * skill list); a list item links {@code self}, and the list links {@code self}. A prompt skill's body is answered
 * exactly as held, and a wrapper skill has none. A member the file does not write (an invocation block, an expansion,
 * a version, a parameter's flags) is {@code null} here and left out on the wire by the {@code Artifact} mix-ins, and so
 * is an empty maintainers list, which the schema answers only when it has an item.
 */
@Component
public class SkillBodies {

    private static final String PACKS = "packs";
    private static final String ARTIFACTS = "artifacts";
    private static final String SKILL = ArtifactKind.SKILL.token();

    private final String basePath;

    /**
     * A mapper whose links start at the base path the controllers are mapped under.
     *
     * @param basePath the server prefix, for example {@code /api/v1}; a trailing {@code /} is ignored
     */
    @Autowired
    public SkillBodies(@Value("${charter.api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /**
     * The list of a pack's skills.
     *
     * @param pack the pack's identity, as the request named it
     * @param skills the skills, in the order they are answered
     * @return the {@code SkillList}, each item linking its own read, the list linking itself
     */
    public SkillList list(String pack, List<SkillView> skills) {
        List<SkillSummary> items = skills.stream()
                .map(skill -> new SkillSummary(
                        skill.id(),
                        skill.title(),
                        skill.description(),
                        SkillSummary.FormEnum.fromValue(skill.form()),
                        links("self", item(skill.packId().value(), skill.id()))))
                .toList();
        return new SkillList(items, links("self", collection(pack)));
    }

    /**
     * The envelope of one skill's pack-scoped read.
     *
     * @param skill the skill
     * @return the {@code Artifact} with kind {@code skill}, the skill in member {@code skill}, and the links
     *     {@code self} and {@code collection}
     */
    public Artifact artifact(SkillView skill) {
        String pack = skill.packId().value();
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", new Link(item(pack, skill.id())));
        links.put("collection", new Link(collection(pack)));
        Artifact envelope = new Artifact(SKILL, skill.id(), pack, links);
        envelope.setSkill(skill(skill));
        return envelope;
    }

    /**
     * The {@code Skill} member: the skill as its file declares it, field by field.
     *
     * @param skill the skill
     * @return the member, with the body exactly as held for a prompt skill and none for a wrapper skill
     */
    public Skill skill(SkillView skill) {
        Skill wire = new Skill(
                skill.schemaVersion(),
                skill.id(),
                skill.title(),
                skill.description(),
                Skill.FormEnum.fromValue(skill.form()),
                skill.triggers(),
                skill.parameters().stream().map(SkillBodies::parameter).toList(),
                skill.tools());
        wire.setInvocation(skill.invocation().map(SkillBodies::invocation).orElse(null));
        wire.setExpandsTo(skill.expandsTo().map(SkillBodies::expansion).orElse(null));
        wire.setVersion(skill.version().orElse(null));
        wire.setMaintainers(skill.maintainers().isEmpty() ? null : skill.maintainers());
        wire.setBody(skill.body().orElse(null));
        return wire;
    }

    private static SkillParameter parameter(SkillView.Parameter parameter) {
        SkillParameter wire = new SkillParameter(parameter.name());
        wire.setRequired(parameter.required().orElse(null));
        wire.setDescription(parameter.description().orElse(null));
        wire.setDefault(parameter.defaultValue().orElse(null));
        return wire;
    }

    private static SkillInvocation invocation(SkillView.Invocation invocation) {
        SkillInvocation wire = new SkillInvocation(invocation.sideEffects());
        wire.setUserInvocable(invocation.userInvocable().orElse(null));
        wire.setModelInvocable(invocation.modelInvocable().orElse(null));
        return wire;
    }

    private static SkillExpansion expansion(SkillView.Expansion expansion) {
        SkillExpansion wire = new SkillExpansion(expansion.target());
        wire.setArgs(expansion.args().orElse(null));
        return wire;
    }

    private String collection(String pack) {
        return href(PACKS, pack, ARTIFACTS, SKILL);
    }

    private String item(String pack, String id) {
        return href(PACKS, pack, ARTIFACTS, SKILL, id);
    }

    private static Map<String, Link> links(String relation, String href) {
        Map<String, Link> links = new LinkedHashMap<>();
        links.put(relation, new Link(href));
        return links;
    }

    private String href(String... segments) {
        return basePath
                + Arrays.stream(segments)
                        .map(segment -> "/" + UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                        .collect(Collectors.joining());
    }
}
