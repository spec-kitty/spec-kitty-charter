package ai.speckitty.charter.artifacts.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.Skill;
import ai.speckitty.charter.api.model.SkillList;
import ai.speckitty.charter.artifacts.application.SkillView;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The skill's wire form maps every field of the view, leaves out what the file does not write, answers a prompt
 * skill's body byte for byte and a wrapper skill without one, and links each read.
 */
class SkillBodiesTest {

    private static final PackIdentifier QA = new PackIdentifier("qa");
    private static final String BODY = "Land the pull request — über alles.\n\n  Keep\tthe spaces. 🚀\n";

    private static final SkillView PROMPT = new SkillView(
            "land-pr",
            QA,
            "1.0",
            "Land a PR",
            "Lands a pull request.",
            "prompt",
            List.of("land the pr", "merge it"),
            List.of(new SkillView.Parameter(
                    "pr", Optional.of(true), Optional.of("The pull request."), Optional.of("1"))),
            Optional.of(new SkillView.Invocation(Optional.of(true), Optional.of(false), List.of("gh-write"))),
            Optional.empty(),
            List.of("Bash"),
            Optional.of("1.2.0"),
            List.of("@spec-kitty/core"),
            Optional.of(BODY));

    private static final SkillView WRAPPER = new SkillView(
            "pr-status",
            QA,
            "1.0",
            "Show the PR status",
            "Lists the open pull requests.",
            "wrapper",
            List.of(),
            List.of(new SkillView.Parameter("mission", Optional.empty(), Optional.empty(), Optional.empty())),
            Optional.empty(),
            Optional.of(new SkillView.Expansion("cli:spec-kitty agent tasks status", Optional.empty())),
            List.of(),
            Optional.empty(),
            List.of(),
            Optional.empty());

    private static final JsonMapper JSON = JsonMapper.builder()
            .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    private final SkillBodies bodies = new SkillBodies("/api/v1/");

    @Test
    void aPromptSkillMapsEveryFieldOfTheView() {
        Skill wire = bodies.skill(PROMPT);

        assertThat(wire.getSchemaVersion()).isEqualTo("1.0");
        assertThat(wire.getId()).isEqualTo("land-pr");
        assertThat(wire.getTitle()).isEqualTo("Land a PR");
        assertThat(wire.getDescription()).isEqualTo("Lands a pull request.");
        assertThat(wire.getForm()).isEqualTo(Skill.FormEnum.PROMPT);
        assertThat(wire.getTriggers()).containsExactly("land the pr", "merge it");
        assertThat(wire.getParameters()).singleElement().satisfies(parameter -> {
            assertThat(parameter.getName()).isEqualTo("pr");
            assertThat(parameter.getRequired()).isTrue();
            assertThat(parameter.getDescription()).isEqualTo("The pull request.");
            assertThat(parameter.getDefault()).isEqualTo("1");
        });
        assertThat(wire.getInvocation()).isNotNull();
        assertThat(wire.getInvocation().getUserInvocable()).isTrue();
        assertThat(wire.getInvocation().getModelInvocable()).isFalse();
        assertThat(wire.getInvocation().getSideEffects()).containsExactly("gh-write");
        assertThat(wire.getExpandsTo()).isNull();
        assertThat(wire.getTools()).containsExactly("Bash");
        assertThat(wire.getVersion()).isEqualTo("1.2.0");
        assertThat(wire.getMaintainers()).containsExactly("@spec-kitty/core");
    }

    @Test
    void aPromptSkillsBodyIsByteIdenticalOnTheWireWithItsFinalNewlineAndUnicode() {
        JsonNode written = JSON.valueToTree(bodies.skill(PROMPT));

        String body = written.get("body").asString();
        assertThat(body.getBytes(StandardCharsets.UTF_8)).isEqualTo(BODY.getBytes(StandardCharsets.UTF_8));
        assertThat(body).endsWith("\n");
        assertThat(written.has("bodyPath")).isFalse();
        assertThat(written.has("overrides")).isFalse();
        assertThat(written.has("enhances")).isFalse();
    }

    @Test
    void aWrapperSkillHasNoBodyAndLeavesOutWhatItsFileDoesNotWrite() {
        JsonNode written = JSON.valueToTree(bodies.skill(WRAPPER));

        assertThat(written.has("body")).isFalse();
        assertThat(written.has("invocation")).isFalse();
        assertThat(written.has("version")).isFalse();
        assertThat(written.has("maintainers")).isFalse();
        assertThat(written.get("form").asString()).isEqualTo("wrapper");
        assertThat(written.get("expandsTo").get("target").asString()).isEqualTo("cli:spec-kitty agent tasks status");
        assertThat(written.get("expandsTo").has("args")).isFalse();
        assertThat(written.get("triggers").isEmpty()).isTrue();
        assertThat(written.get("tools").isEmpty()).isTrue();
        JsonNode parameter = written.get("parameters").get(0);
        assertThat(parameter.get("name").asString()).isEqualTo("mission");
        assertThat(parameter.has("required")).isFalse();
        assertThat(parameter.has("description")).isFalse();
        assertThat(parameter.has("default")).isFalse();
    }

    @Test
    void theEnvelopeIsKindSkillWithTheMemberAndLinksItselfAndItsCollection() {
        Artifact envelope = bodies.artifact(PROMPT);

        assertThat(envelope.getKind()).isEqualTo("skill");
        assertThat(envelope.getId()).isEqualTo("land-pr");
        assertThat(envelope.getPackId()).isEqualTo("qa");
        assertThat(envelope.getLinks()).containsOnlyKeys("self", "collection");
        assertThat(envelope.getLinks().get("self").getHref()).isEqualTo("/api/v1/packs/qa/artifacts/skill/land-pr");
        assertThat(envelope.getLinks().get("collection").getHref()).isEqualTo("/api/v1/packs/qa/artifacts/skill");
        assertThat(envelope.getSkill()).isEqualTo(bodies.skill(PROMPT));
    }

    @Test
    void theListSummarisesEachSkillInTheGivenOrderAndLinksItself() {
        SkillList list = bodies.list("qa", List.of(PROMPT, WRAPPER));

        assertThat(list.getItems()).extracting(item -> item.getId()).containsExactly("land-pr", "pr-status");
        assertThat(list.getItems())
                .extracting(item -> item.getForm().getValue())
                .containsExactly("prompt", "wrapper");
        assertThat(list.getItems().get(1).getTitle()).isEqualTo("Show the PR status");
        assertThat(list.getItems().get(1).getDescription()).isEqualTo("Lists the open pull requests.");
        assertThat(list.getItems().get(1).getLinks().get("self").getHref())
                .isEqualTo("/api/v1/packs/qa/artifacts/skill/pr-status");
        assertThat(list.getLinks().get("self").getHref()).isEqualTo("/api/v1/packs/qa/artifacts/skill");
    }

    @Test
    void anIdIsEncodedAsOnePathSegment() {
        SkillView odd = new SkillView(
                "a b",
                new PackIdentifier("q/a"),
                "1.0",
                "T",
                "D",
                "wrapper",
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.of(new SkillView.Expansion("builtin:x", Optional.of("--y"))),
                List.of(),
                Optional.empty(),
                List.of(),
                Optional.empty());

        assertThat(bodies.artifact(odd).getLinks().get("self").getHref())
                .isEqualTo("/api/v1/packs/q%2Fa/artifacts/skill/a%20b");
        assertThat(bodies.skill(odd).getExpandsTo().getArgs()).isEqualTo("--y");
    }
}
