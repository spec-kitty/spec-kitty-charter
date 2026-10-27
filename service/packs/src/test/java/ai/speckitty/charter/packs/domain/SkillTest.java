package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Skill entity")
class SkillTest {

    private static final PackIdentifier PACK = new PackIdentifier("01PACK");

    private static Skill.Builder prompt(String id) {
        return Skill.builder()
                .id(new SkillIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Land a PR")
                .description("Use when a pull request needs landing.")
                .form(SkillForm.PROMPT)
                .bodyPath(Optional.of(id + ".skill.md"))
                .body(Optional.of("Goal: land it.\n"));
    }

    private static Skill.Builder wrapper(String id) {
        return Skill.builder()
                .id(new SkillIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Status")
                .description("Shows the status.")
                .form(SkillForm.WRAPPER)
                .expandsTo(Optional.of(new SkillExpansion("cli:spec-kitty status", Optional.empty())));
    }

    @Test
    @DisplayName("a prompt skill builds with its body, kept as written, and is addressed as skill:<id>")
    void aPromptSkillBuilds() {
        SkillInvocation invocation =
                new SkillInvocation(Optional.of(true), Optional.empty(), List.of("git-push", "gh-write"));
        Skill skill = prompt("land-pr")
                .triggers(List.of("land this PR", "land PR"))
                .parameters(
                        List.of(new SkillParameter("pr", Optional.of(true), Optional.of("The PR"), Optional.empty())))
                .invocation(Optional.of(invocation))
                .version(Optional.of("1.0.0"))
                .maintainers(List.of("@spec-kitty/core"))
                .build();

        assertThat(skill.kind()).isEqualTo(ArtifactKind.SKILL);
        assertThat(skill.urn().toString()).isEqualTo("skill:land-pr");
        assertThat(skill.label()).isEqualTo("Land a PR");
        assertThat(skill.body()).contains("Goal: land it.\n");
        assertThat(skill.invocation()).contains(invocation);
        assertThat(skill.invocation().orElseThrow().modelInvocable())
                .as("side effects do not force model_invocable off: absent stays absent")
                .isEmpty();
        assertThat(skill.tools()).as("no default is filled in").isEmpty();
        assertThat(skill.relationships()).isEmpty();
    }

    @Test
    @DisplayName("a wrapper skill builds with its expansion and no body")
    void aWrapperSkillBuilds() {
        Skill skill = wrapper("status").build();

        assertThat(skill.form()).isEqualTo(SkillForm.WRAPPER);
        assertThat(skill.bodyPath()).isEmpty();
        assertThat(skill.body()).isEmpty();
    }

    @Test
    @DisplayName("each form's shape is enforced")
    void formsAreEnforced() {
        assertThatThrownBy(() -> prompt("p").body(Optional.empty()).build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A prompt skill needs a body path and its body");
        assertThatThrownBy(() -> prompt("p").bodyPath(Optional.empty()).build())
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> prompt("p")
                        .expandsTo(Optional.of(new SkillExpansion("builtin:x", Optional.empty())))
                        .build())
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> wrapper("w").expandsTo(Optional.empty()).build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A wrapper skill needs an expansion");
        assertThatThrownBy(() -> wrapper("w").bodyPath(Optional.of("w.md")).build())
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("the id grammar and length, blank fields and an unknown form are refused")
    void malformedPartsAreRefused() {
        assertThatThrownBy(() -> new SkillIdentifier("Land-PR")).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new SkillIdentifier("land--pr")).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new SkillIdentifier("a".repeat(65))).isInstanceOf(ConstraintBreachException.class);
        assertThat(new SkillIdentifier("a".repeat(64)).value()).hasSize(64);
        assertThatThrownBy(() -> prompt("p").title(" ").build()).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> prompt("p").description(" ").build()).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new SkillParameter(" ", Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new SkillExpansion(" ", Optional.empty()))
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> SkillForm.fromToken("macro")).isInstanceOf(IllegalArgumentException.class);
        assertThat(SkillForm.fromToken("prompt")).isEqualTo(SkillForm.PROMPT);
    }

    @Test
    @DisplayName("two skills are equal by identity, whatever their other fields")
    void identityEquality() {
        assertThat(prompt("a").build())
                .isEqualTo(prompt("a").title("Other").build())
                .hasSameHashCodeAs(prompt("a").build())
                .isNotEqualTo(prompt("b").build());
    }
}
